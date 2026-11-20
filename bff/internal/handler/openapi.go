package handler

import (
	"encoding/json"
	"html"
	"net/http"

	"github.com/gin-gonic/gin"
)

const openAPIVersion = "3.1.0"

var builtInScreenIDs = []string{
	"home",
	"store-home",
	"catalog",
	"profile",
	"form-demo",
	"containers",
	"watch-status",
}

func registerOpenAPIRoutes(engine *gin.Engine, serviceName string, requireAuth bool) {
	specBytes := mustBuildOpenAPISpec(serviceName, requireAuth)
	docsHTML := buildDocsHTML(serviceName)

	engine.GET("/openapi.json", func(c *gin.Context) {
		c.Data(http.StatusOK, "application/json; charset=utf-8", specBytes)
	})

	engine.GET("/docs", func(c *gin.Context) {
		c.Header("Content-Security-Policy", "default-src 'self'; script-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; style-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; font-src 'self' https://cdn.jsdelivr.net data:; img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none';")
		c.Data(http.StatusOK, "text/html; charset=utf-8", docsHTML)
	})
}

func buildDocsHTML(serviceName string) []byte {
	title := html.EscapeString(serviceName) + " — API Docs"
	page := `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>` + title + `</title>
  <style>
    body { margin: 0; padding: 0; font-family: system-ui, -apple-system, sans-serif; }
  </style>
</head>
<body>
  <script id="api-reference" data-url="/openapi.json"></script>
  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
  <noscript>
    <p>OpenAPI 3.1 schema available at <a href="/openapi.json">/openapi.json</a>.</p>
  </noscript>
</body>
</html>
`
	return []byte(page)
}

func mustBuildOpenAPISpec(serviceName string, requireAuth bool) []byte {
	var apiSecurity []map[string][]string
	if requireAuth {
		apiSecurity = []map[string][]string{{"bearerAuth": {}}}
	}

	etagHeader := map[string]any{
		"description": "Strong or weak SHA-256 content entity tag",
		"schema":      map[string]any{"type": "string"},
	}
	cacheHeader := map[string]any{
		"description": "HTTP cache policy",
		"schema":      map[string]any{"type": "string", "example": defaultCacheControl},
	}
	jsonContent := func(schema map[string]any) map[string]any {
		return map[string]any{"application/json": map[string]any{"schema": schema}}
	}
	ifNoneMatchParam := map[string]any{
		"name":        "If-None-Match",
		"in":          "header",
		"required":    false,
		"description": "Client ETag for conditional 304 Not Modified validation",
		"schema":      map[string]any{"type": "string"},
	}

	spec := map[string]any{
		"openapi": openAPIVersion,
		"info": map[string]any{
			"title":       serviceName,
			"version":     "1.0.0",
			"description": "Abeta KMP Server-Driven UI (SDUI) Backend-For-Frontend HTTP API.",
		},
		"servers": []map[string]any{
			{"url": "/", "description": "Current BFF origin"},
		},
		"paths": map[string]any{
			"/healthz": map[string]any{
				"get": map[string]any{
					"summary":     "Liveness probe",
					"operationId": "getHealthz",
					"tags":        []string{"Operations"},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "Service is alive",
							"content":     jsonContent(map[string]any{"type": "object"}),
						},
					},
				},
			},
			"/readyz": map[string]any{
				"get": map[string]any{
					"summary":     "Readiness probe",
					"operationId": "getReadyz",
					"tags":        []string{"Operations"},
					"responses": map[string]any{
						"200": map[string]any{"description": "Service dependencies ready"},
						"503": map[string]any{"description": "Service degraded"},
					},
				},
			},
			"/metrics": map[string]any{
				"get": map[string]any{
					"summary":     "Prometheus / JSON RED metrics",
					"operationId": "getMetrics",
					"tags":        []string{"Operations"},
					"parameters": []map[string]any{
						{
							"name":        "format",
							"in":          "query",
							"required":    false,
							"description": "Set to json for JSON metrics instead of Prometheus text format",
							"schema":      map[string]any{"type": "string", "enum": []string{"json"}},
						},
					},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "RED metrics snapshot",
							"content": map[string]any{
								"text/plain":       map[string]any{"schema": map[string]any{"type": "string"}},
								"application/json": map[string]any{"schema": map[string]any{"type": "object"}},
							},
						},
					},
				},
			},
			"/openapi.json": map[string]any{
				"get": map[string]any{
					"summary":     "OpenAPI 3.1 specification",
					"operationId": "getOpenAPI",
					"tags":        []string{"Documentation"},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "OpenAPI 3.1 JSON document",
							"content":     jsonContent(map[string]any{"type": "object"}),
						},
					},
				},
			},
			"/docs": map[string]any{
				"get": map[string]any{
					"summary":     "Interactive API documentation",
					"operationId": "getDocs",
					"tags":        []string{"Documentation"},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "Scalar API reference HTML page",
							"content": map[string]any{
								"text/html": map[string]any{"schema": map[string]any{"type": "string"}},
							},
						},
					},
				},
			},
			"/api/v1/screens": map[string]any{
				"get": map[string]any{
					"summary":     "List registered SDUI screen IDs",
					"operationId": "listScreens",
					"tags":        []string{"SDUI Screens"},
					"security":    apiSecurity,
					"responses": map[string]any{
						"200": map[string]any{
							"description": "Available screen identifiers",
							"headers": map[string]any{
								"ETag":          etagHeader,
								"Cache-Control": cacheHeader,
							},
							"content": jsonContent(envelopeSchema(map[string]any{
								"type": "object",
								"properties": map[string]any{
									"screens": map[string]any{
										"type":    "array",
										"items":   map[string]any{"type": "string", "enum": builtInScreenIDs},
										"example": builtInScreenIDs,
									},
								},
							})),
						},
						"304": map[string]any{"description": "Not Modified"},
						"401": map[string]any{"description": "Unauthorized"},
					},
				},
			},
			"/api/v1/screens/{screenId}": map[string]any{
				"get": map[string]any{
					"summary":     "Fetch an SDUI screen payload",
					"operationId": "getScreen",
					"tags":        []string{"SDUI Screens"},
					"security":    apiSecurity,
					"parameters": []map[string]any{
						{
							"name":        "screenId",
							"in":          "path",
							"required":    true,
							"description": "Screen identifier (built-in: home, store-home, catalog, profile, form-demo, containers, watch-status)",
							"schema":      map[string]any{"type": "string", "enum": builtInScreenIDs},
						},
						ifNoneMatchParam,
					},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "SDUI PageResponse envelope",
							"headers": map[string]any{
								"ETag":          etagHeader,
								"Cache-Control": cacheHeader,
							},
							"content": jsonContent(envelopeSchema(refSchema("PageResponse"))),
						},
						"304": map[string]any{"description": "Not Modified"},
						"400": map[string]any{"description": "Bad Request"},
						"401": map[string]any{"description": "Unauthorized"},
						"404": map[string]any{"description": "Screen Not Found"},
					},
				},
			},
			"/api/v1/actions/execute": map[string]any{
				"post": map[string]any{
					"summary":     "Execute an SDUI action trigger",
					"operationId": "executeAction",
					"tags":        []string{"SDUI Actions"},
					"security":    apiSecurity,
					"requestBody": map[string]any{
						"required": true,
						"content":  jsonContent(refSchema("ActionExecutionInput")),
					},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "Action execution outcome",
							"content":     jsonContent(envelopeSchema(refSchema("ActionExecutionResult"))),
						},
						"400": map[string]any{"description": "Malformed payload or unsupported action"},
						"401": map[string]any{"description": "Unauthorized"},
						"422": map[string]any{"description": "Declarative form validation failed"},
					},
				},
			},
			"/api/v1/orders/express": map[string]any{
				"post": map[string]any{
					"summary":     "Place an Abeta Fresh 8-minute express order",
					"operationId": "placeExpressOrder",
					"tags":        []string{"SDUI Orders"},
					"security":    apiSecurity,
					"requestBody": map[string]any{
						"required": false,
						"content": jsonContent(map[string]any{
							"type":                 "object",
							"additionalProperties": true,
							"properties": map[string]any{
								"store_brand":       map[string]any{"type": "string", "example": "Abeta Fresh"},
								"delivery_eta_mins": map[string]any{"type": "integer", "example": 8},
								"coupon_code":       map[string]any{"type": "string", "example": "FRESH20"},
								"source":            map[string]any{"type": "string", "example": "store_home"},
							},
						}),
					},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "Express order confirmation with follow-up celebration and toast actions",
							"content":     jsonContent(envelopeSchema(refSchema("ActionExecutionResult"))),
						},
						"400": map[string]any{"description": "Malformed express order payload"},
						"401": map[string]any{"description": "Unauthorized"},
					},
				},
			},
			"/api/v1/catalog/tokens": map[string]any{
				"get": map[string]any{
					"summary":     "Fetch active SDUI design token catalog",
					"operationId": "getTokenCatalog",
					"tags":        []string{"SDUI Design System"},
					"security":    apiSecurity,
					"parameters":  []map[string]any{ifNoneMatchParam},
					"responses": map[string]any{
						"200": map[string]any{
							"description": "Design token catalog envelope",
							"headers": map[string]any{
								"ETag":          etagHeader,
								"Cache-Control": cacheHeader,
							},
							"content": jsonContent(envelopeSchema(refSchema("TokenCatalog"))),
						},
						"304": map[string]any{"description": "Not Modified"},
						"401": map[string]any{"description": "Unauthorized"},
					},
				},
			},
		},
		"components": map[string]any{
			"securitySchemes": map[string]any{
				"bearerAuth": map[string]any{
					"type":         "http",
					"scheme":       "bearer",
					"bearerFormat": "JWT",
				},
			},
			"schemas": openAPIComponentSchemas(),
		},
	}

	raw, err := json.Marshal(spec)
	if err != nil {
		panic(err)
	}
	return raw
}
