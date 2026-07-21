package server

import "encoding/json"

// ─────────────────────────────────────────────────────────────────────────────
// UDS protocol message types
// ─────────────────────────────────────────────────────────────────────────────

// Request is a client-to-server RPC call.
type Request struct {
	ID     string          `json:"id"`
	Method string          `json:"method"`
	Params json.RawMessage `json:"params,omitempty"`
}

// Response is a server-to-client RPC reply (on the same connection as the request).
type Response struct {
	ID     string          `json:"id"`
	Result json.RawMessage `json:"result,omitempty"`
	Error  *ResponseError  `json:"error,omitempty"`
}

// ResponseError carries an error code and human-readable message.
type ResponseError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

// Event is a server-to-client push notification (on a dedicated event connection).
type Event struct {
	Event string          `json:"event"`
	Data  json.RawMessage `json:"data"`
}

// ─── helper constructors ────────────────────────────────────────────────────

func OkResponse(id string) Response {
	return Response{ID: id, Result: json.RawMessage(`{"ok":true}`)}
}

func DataResponse(id string, data any) (Response, error) {
	b, err := json.Marshal(data)
	if err != nil {
		return ErrorResponse(id, 500, err.Error()), nil
	}
	return Response{ID: id, Result: b}, nil
}

func ErrorResponse(id string, code int, msg string) Response {
	return Response{
		ID:    id,
		Error: &ResponseError{Code: code, Message: msg},
	}
}

func NewEvent(event string, data any) (Event, error) {
	b, err := json.Marshal(data)
	if err != nil {
		return Event{}, err
	}
	return Event{Event: event, Data: b}, nil
}
