package server

import (
	"encoding/json"
	"strings"

	"cfa/native/app"
	"cfa/native/config"

	"github.com/metacubex/mihomo/hub"
)

// RegisterConfigMethods registers config compilation/inspection method handlers.
func RegisterConfigMethods(s *Server) {
	// config.compilePreview — compile a config request and return the result.
	// NOTE: The actual compilation goes through the Rust override engine (liboverride.so).
	// In UDS mode, this will be called after the Kotlin side sends the request JSON.
	// For now we expose the Go-side config functions directly.
	s.Handle("config.compilePreview", func(req Request) Response {
		// This requires the Rust override engine. In the UDS server, we delegate
		// to the Kotlin side which holds the Rust library. The Kotlin side will
		// call this method only after it has already done the Rust compilation
		// and needs Go to load the result.
		return ErrorResponse(req.ID, 501, "config.compilePreview not available in UDS mode; use config.loadCompiledRaw after Kotlin-side Rust compilation")
	})

	// config.loadCompiledRaw — parse and apply a compiled raw config JSON.
	s.Handle("config.loadCompiledRaw", func(req Request) Response {
		var p struct {
			ConfigRawJSON string `json:"configRawJson"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		rawCfg, cfg, err := config.ParseCompiledRaw(p.ConfigRawJSON)
		if err != nil {
			return ErrorResponse(req.ID, 500, err.Error())
		}

		// Apply the config via mihomo hub.
		hub.ApplyConfig(cfg)
		app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

		type loadResult struct {
			Success     bool     `json:"success"`
			Fingerprint string   `json:"fingerprint"`
			Warnings    []string `json:"warnings"`
			Error       string   `json:"error,omitempty"`
		}

		result := loadResult{
			Success:     true,
			Fingerprint: rawCfg.Fingerprint,
		}
		return DataResponse(req.ID, result)
	})

	// config.compiledRawResultError — extract error from compile result JSON.
	s.Handle("config.compiledRawResultError", func(req Request) Response {
		var p struct {
			ResultJSON string `json:"resultJson"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		type compileRawResult struct {
			Success bool   `json:"success"`
			Error   string `json:"error"`
		}
		var result compileRawResult
		if err := json.Unmarshal([]byte(p.ResultJSON), &result); err != nil {
			return ErrorResponse(req.ID, 500, err.Error())
		}
		if result.Success {
			return DataResponse(req.ID, map[string]any{"error": nil})
		}
		msg := strings.TrimSpace(result.Error)
		if msg == "" {
			msg = "compile raw config failed"
		}
		return DataResponse(req.ID, map[string]string{"error": msg})
	})

	// config.compiledRawResultSummary — extract summary from compile result JSON.
	s.Handle("config.compiledRawResultSummary", func(req Request) Response {
		var p struct {
			ResultJSON string `json:"resultJson"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		type compileRawResult struct {
			Success     bool     `json:"success"`
			Fingerprint string   `json:"fingerprint"`
			ConfigRaw   string   `json:"configRaw"`
			Warnings    []string `json:"warnings"`
			Error       string   `json:"error"`
		}
		type compileRawSummary struct {
			Success           bool     `json:"success"`
			Fingerprint       string   `json:"fingerprint"`
			Warnings          []string `json:"warnings"`
			Error             string   `json:"error"`
			TunIncludePackage []string `json:"tunIncludePackage,omitempty"`
			TunExcludePackage []string `json:"tunExcludePackage,omitempty"`
		}

		var result compileRawResult
		if err := json.Unmarshal([]byte(p.ResultJSON), &result); err != nil {
			return ErrorResponse(req.ID, 500, err.Error())
		}

		summary := compileRawSummary{
			Success:     result.Success,
			Fingerprint: result.Fingerprint,
			Warnings:    result.Warnings,
			Error:       result.Error,
		}

		if result.Success && strings.TrimSpace(result.ConfigRaw) != "" {
			includePackage, excludePackage, err := config.QueryTunPackagesFromCompiledRaw(result.ConfigRaw)
			if err != nil {
				summary.Warnings = append(summary.Warnings, "inspect tun packages failed: "+err.Error())
			} else {
				summary.TunIncludePackage = includePackage
				summary.TunExcludePackage = excludePackage
			}
		}

		return DataResponse(req.ID, summary)
	})

	// config.inspectCompiledGroups — query proxy groups from compiled raw.
	s.Handle("config.inspectCompiledGroups", func(req Request) Response {
		var p struct {
			ConfigRawJSON        string `json:"configRawJson"`
			ProfileDir           string `json:"profileDir"`
			ExcludeNotSelectable bool   `json:"excludeNotSelectable"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		groups, err := config.QueryProxyGroupsFromCompiledRaw(p.ConfigRawJSON, p.ProfileDir, p.ExcludeNotSelectable)
		if err != nil {
			return ErrorResponse(req.ID, 500, err.Error())
		}

		type inspectResult struct {
			Success bool        `json:"success"`
			Payload interface{} `json:"payload"`
		}
		return DataResponse(req.ID, inspectResult{Success: true, Payload: groups})
	})

	// config.inspectTunRouteExcludeAddress — query tun route exclude addresses.
	s.Handle("config.inspectTunRouteExcludeAddress", func(req Request) Response {
		var p struct {
			ConfigRawJSON string `json:"configRawJson"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		addresses, err := config.QueryTunRouteExcludeAddressFromCompiledRaw(p.ConfigRawJSON)
		if err != nil {
			return ErrorResponse(req.ID, 500, err.Error())
		}

		type inspectResult struct {
			Success bool        `json:"success"`
			Payload interface{} `json:"payload"`
		}
		return DataResponse(req.ID, inspectResult{Success: true, Payload: addresses})
	})

	// config.convertMrsToText — convert .mrs rule file to text.
	s.Handle("config.convertMrsToText", func(req Request) Response {
		var p struct {
			FilePath string `json:"filePath"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		text, err := config.ConvertMrsToText(p.FilePath)
		if err != nil {
			return ErrorResponse(req.ID, 500, err.Error())
		}
		return DataResponse(req.ID, map[string]string{"text": text})
	})
}
