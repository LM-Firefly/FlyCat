package server

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"time"
)

// ─────────────────────────────────────────────────────────────────────────────
// UDS Server — accepts connections on a Unix domain socket and dispatches
// length-prefixed JSON-RPC requests to registered method handlers.
// ─────────────────────────────────────────────────────────────────────────────

// Handler processes a single request and returns a response.
// The optional net.Conn parameter allows handlers that need raw socket access
// (e.g., for receiving file descriptors via SCM_RIGHTS).
type Handler func(req Request) Response

// FdHandler is a special handler that also receives the raw connection,
// used for methods that require SCM_RIGHTS fd passing.
type FdHandler func(req Request, conn net.Conn) Response

// Server listens on a Unix domain socket and multiplexes requests.
type Server struct {
	socketPath    string
	listener      net.Listener
	handlers      map[string]Handler
	fdHandlers    map[string]FdHandler // handlers that need raw conn access
	eventMu       sync.RWMutex
	eventConns    []io.Writer // subscribers for event push
	connCount     atomic.Int64
	callbackConn  net.Conn // reverse callback connection from Kotlin
	callbackMu    sync.Mutex
	callbackReady chan struct{} // closed when callback is registered
}

// New creates a new Server bound to the given socket path.
func New(socketPath string) *Server {
	return &Server{
		socketPath:    socketPath,
		handlers:      make(map[string]Handler),
		fdHandlers:    make(map[string]FdHandler),
		callbackReady: make(chan struct{}),
	}
}

// Handle registers a method handler.
func (s *Server) Handle(method string, h Handler) {
	s.handlers[method] = h
}

// HandleFd registers a handler that receives the raw connection (for SCM_RIGHTS).
func (s *Server) HandleFd(method string, h FdHandler) {
	s.fdHandlers[method] = h
}

// RegisterCallback stores the reverse callback connection from Kotlin.
func (s *Server) RegisterCallback(conn net.Conn) {
	s.callbackMu.Lock()
	defer s.callbackMu.Unlock()
	s.callbackConn = conn
	select {
	case <-s.callbackReady:
	default:
		close(s.callbackReady)
	}
	log.Printf("[UDS] callback connection registered")
}

// CallCallback sends a request to the Kotlin callback handler and waits for a response.
// This is used by the Go core when it needs to query socket owners.
// Returns the response result JSON, or an error if the callback is not available.
func (s *Server) CallCallback(method string, params any) (string, error) {
	s.callbackMu.Lock()
	conn := s.callbackConn
	s.callbackMu.Unlock()

	if conn == nil {
		return "", fmt.Errorf("callback connection not registered")
	}

	// Build the request.
	paramsBytes, err := json.Marshal(params)
	if err != nil {
		return "", fmt.Errorf("marshal callback params: %w", err)
	}

	req := Request{
		ID:     fmt.Sprintf("cb-%d", time.Now().UnixNano()),
		Method: method,
		Params: paramsBytes,
	}

	// Send the request.
	if err := WriteMessage(conn, req); err != nil {
		return "", fmt.Errorf("send callback request: %w", err)
	}

	// Read the response.
	var resp Response
	if err := ReadMessage(conn, &resp); err != nil {
		return "", fmt.Errorf("read callback response: %w", err)
	}

	if resp.Error != nil {
		return "", fmt.Errorf("callback error %d: %s", resp.Error.Code, resp.Error.Message)
	}

	return string(resp.Result), nil
}

// WaitForCallback blocks until the callback connection is registered or timeout.
func (s *Server) WaitForCallback(timeout time.Duration) bool {
	select {
	case <-s.callbackReady:
		return true
	case <-time.After(timeout):
		return false
	}
}

// Subscribe registers a writer for event push notifications.
func (s *Server) Subscribe(w io.Writer) {
	s.eventMu.Lock()
	defer s.eventMu.Unlock()
	s.eventConns = append(s.eventConns, w)
}

// Unsubscribe removes a writer from the event push list.
func (s *Server) Unsubscribe(w io.Writer) {
	s.eventMu.Lock()
	defer s.eventMu.Unlock()
	for i, c := range s.eventConns {
		if c == w {
			s.eventConns = append(s.eventConns[:i], s.eventConns[i+1:]...)
			return
		}
	}
}

// PublishEvent sends an event to all subscribed writers.
// Broken pipes are silently removed from the subscriber list.
func (s *Server) PublishEvent(evt Event) {
	s.eventMu.Lock()
	defer s.eventMu.Unlock()

	var alive []io.Writer
	for _, w := range s.eventConns {
		if err := WriteMessage(w, evt); err != nil {
			// Connection is dead; drop it.
			log.Printf("[UDS] removing dead event subscriber: %v", err)
			continue
		}
		alive = append(alive, w)
	}
	s.eventConns = alive
}

// Start begins listening. Blocks until Stop is called or a fatal error occurs.
func (s *Server) Start() error {
	// Remove stale socket if present.
	_ = os.Remove(s.socketPath)

	ln, err := net.Listen("unix", s.socketPath)
	if err != nil {
		return fmt.Errorf("listen %s: %w", s.socketPath, err)
	}
	s.listener = ln

	log.Printf("[UDS] listening on %s", s.socketPath)

	for {
		conn, err := ln.Accept()
		if err != nil {
			// Check if we were stopped.
			if s.listener == nil {
				return nil
			}
			log.Printf("[UDS] accept error: %v", err)
			continue
		}
		go s.handleConn(conn)
	}
}

// Stop gracefully shuts down the server and removes the socket file.
func (s *Server) Stop() {
	if s.listener != nil {
		_ = s.listener.Close()
		s.listener = nil
	}
	_ = os.Remove(s.socketPath)
	log.Printf("[UDS] stopped, removed %s", s.socketPath)
}

func (s *Server) handleConn(conn net.Conn) {
	id := s.connCount.Add(1)
	log.Printf("[UDS] conn #%d established from %s", id, conn.RemoteAddr())
	defer func() {
		s.Unsubscribe(conn)
		_ = conn.Close()
		log.Printf("[UDS] conn #%d closed", id)
	}()

	// Read the first message. If it's a log.subscribe, promote this
	// connection to an event-only subscriber and block until EOF.
	var firstReq Request
	if err := ReadMessage(conn, &firstReq); err != nil {
		if err != io.EOF {
			log.Printf("[UDS] conn #%d read error: %v", id, err)
		}
		return
	}

	if firstReq.Method == "log.subscribe" || firstReq.Method == "event.subscribe" {
		// Register as event subscriber.
		s.Subscribe(conn)
		log.Printf("[UDS] conn #%d subscribed to events", id)

		// Send the subscription acknowledgement.
		_ = WriteMessage(conn, OkResponse(firstReq.ID))

		// Block until the client disconnects.
		buf := make([]byte, 1)
		for {
			if _, err := conn.Read(buf); err != nil {
				return
			}
		}
	}

	if firstReq.Method == "callback.register" {
		// Register as the reverse callback connection.
		s.RegisterCallback(conn)
		log.Printf("[UDS] conn #%d registered as callback handler", id)

		// Send acknowledgement.
		_ = WriteMessage(conn, OkResponse(firstReq.ID))

		// This connection is now owned by the callback system.
		// Block until disconnect (read errors will trigger cleanup).
		buf := make([]byte, 1)
		for {
			if _, err := conn.Read(buf); err != nil {
				s.callbackMu.Lock()
				if s.callbackConn == conn {
					s.callbackConn = nil
					s.callbackReady = make(chan struct{})
				}
				s.callbackMu.Unlock()
				log.Printf("[UDS] conn #%d callback handler disconnected", id)
				return
			}
		}
	}

	// Normal RPC connection: dispatch the first request then loop.
	resp := s.dispatch(firstReq, conn)
	if err := WriteMessage(conn, resp); err != nil {
		log.Printf("[UDS] conn #%d write error: %v", id, err)
		return
	}

	for {
		var req Request
		if err := ReadMessage(conn, &req); err != nil {
			if err != io.EOF {
				log.Printf("[UDS] conn #%d read error: %v", id, err)
			}
			return
		}

		resp := s.dispatch(req, conn)
		if err := WriteMessage(conn, resp); err != nil {
			log.Printf("[UDS] conn #%d write error: %v", id, err)
			return
		}
	}
}

func (s *Server) dispatch(req Request, conn net.Conn) Response {
	// Check fd-aware handlers first.
	if fdh, ok := s.fdHandlers[req.Method]; ok {
		return fdh(req, conn)
	}
	handler, ok := s.handlers[req.Method]
	if !ok {
		return ErrorResponse(req.ID, 404, "unknown method: "+req.Method)
	}

	defer func() {
		if r := recover(); r != nil {
			log.Printf("[UDS] panic in handler %s: %v", req.Method, r)
		}
	}()

	return handler(req)
}
