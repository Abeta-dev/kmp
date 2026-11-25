package domain_test

import (
	"go/parser"
	"go/token"
	"path/filepath"
	"strings"
	"testing"
)

func TestCleanArchitectureDependencyRule(t *testing.T) {
	forbiddenInPureLayers := []string{
		"github.com/gin-gonic/gin",
		"net/http",
		"database/sql",
		"github.com/jackc/pgx",
		"internal/infra",
		"internal/handler",
	}

	pureDirs := []string{
		filepath.Join("..", "domain"),
		filepath.Join("..", "usecase"),
		filepath.Join("..", "..", "pkg", "sdui", "theming"),
		filepath.Join("..", "..", "pkg", "sdui", "actions"),
		filepath.Join("..", "..", "pkg", "sdui", "atoms"),
		filepath.Join("..", "..", "pkg", "sdui", "snippets"),
		filepath.Join("..", "..", "pkg", "sdui", "analytics"),
		filepath.Join("..", "..", "pkg", "sdui", "molecules"),
		filepath.Join("..", "..", "pkg", "sdui", "cache"),
	}

	fset := token.NewFileSet()
	for _, dir := range pureDirs {
		files, err := filepath.Glob(filepath.Join(dir, "*.go"))
		if err != nil {
			t.Fatalf("glob %s: %v", dir, err)
		}
		for _, file := range files {
			if strings.HasSuffix(file, "_test.go") {
				continue
			}
			f, err := parser.ParseFile(fset, file, nil, parser.ImportsOnly)
			if err != nil {
				t.Fatalf("parse %s: %v", file, err)
			}
			for _, imp := range f.Imports {
				path := strings.Trim(imp.Path.Value, `"`)
				for _, bad := range forbiddenInPureLayers {
					if strings.Contains(path, bad) {
						t.Errorf("dependency rule violation: %s imports %q", file, path)
					}
				}
			}
		}
	}
}
