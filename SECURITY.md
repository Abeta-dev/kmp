# Security Policy

## Supported Versions

The `abeta-dev/kmp` maintainers provide security updates for the latest minor release line of `dev.abeta.kmp` artifacts and the `bff/` service:

| Version | Supported |
| :--- | :--- |
| `0.1.x` | Yes |
| `< 0.1.0` | No |

---

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, discussions, or pull requests.**

Instead, please report them privately using **GitHub Private Vulnerability Reporting**:

1. Navigate to the repository's **Security** tab on GitHub (`https://github.com/abeta-dev/kmp/security/advisories`).
2. Click **"Report a vulnerability"**.
3. Provide as much detail as possible:
   - Affected module (`kmp/*` or `bff/*`) and version
   - Reproduction steps or proof-of-concept
   - Impact assessment (e.g., SDUI payload injection, action hijacking, auth bypass, SSRF)
   - Suggested remediation, if any

### Response Timeline

- **Acknowledgment**: Within 48 hours of receipt
- **Triage & Assessment**: Within 5 business days
- **Remediation & Coordinated Disclosure**: Prioritized by severity (CVSS) and released with an advisory

---

## Security Practices in `abeta-dev/kmp`

- **Action Allowlisting**: The KMP `ActionRegistry` and `ActionDispatcher` reject unknown or unregistered action types rather than executing arbitrary reflection or unvalidated URLs.
- **Pure Wire Contracts**: `sdui-contracts` carries declarative data only—no executable code or implicit side effects.
- **Client Transport & Storage Hardening (`kmp/core`, `kmp/state`, `kmp/network`)**: Supports TLS HTTP/2 ALPN, bearer token refresh via `AuthTokenProvider`, hostname SHA-256 certificate pinning via `NetworkConfig.certificatePins` (`CertificatePinMatcher` shared across JVM/OkHttp `CertificatePinner` and Apple Darwin `NSURLSession` `PlatformHttpEngine.apple.kt` on iOS, macOS, and watchOS), `SecureKeyValueStore` (`AppleKeychain` `SecItem*` w/ fallback, JVM AES-256-GCM + `Preferences`, WasmJS `sessionStorage`, exposed via `createSecurePersistentKeyValueStore(namespace)` in `state`), and `BiometricAuthenticator` (`LAContext` on Apple).
- **No Committed Secrets**: `.gitignore` blocks `.env*`, keystores, and credentials; CI workflows (`quality-security.yml`) scan commits for accidental secret exposure and brand/path hygiene.
- **BFF Hardening (`bff/`)**: Leverages `github.com/abeta-dev/go-libs@v0.4.0` middleware for request IDs, structured logging, recovery, security headers, optional `ginmw.GlobalRateLimit()`, optional `ginmw.AuthMiddleware()` (`AUTH_REQUIRED=true` + fail-closed `JWT_SECRET` check), and explicit CSP headers on `/docs`.
