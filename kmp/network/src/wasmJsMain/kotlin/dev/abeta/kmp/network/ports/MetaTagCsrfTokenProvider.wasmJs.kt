@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.network.ports

@JsFun(
    "(metaName) => { try { " +
        "if (typeof document === 'undefined' || !document || " +
        "typeof document.getElementsByTagName !== 'function') { return null; } " +
        "const metas = document.getElementsByTagName('meta'); " +
        "for (let i = 0; i < metas.length; i++) { " +
        "const el = metas[i]; " +
        "if (el && typeof el.getAttribute === 'function' && el.getAttribute('name') === metaName) { " +
        "const content = el.getAttribute('content'); " +
        "if (content && content.trim().length > 0) { return content.trim(); } " +
        "} } } catch (e) {} return null; }",
)
private external fun jsReadMetaTagContent(metaName: String): String?

/**
 * Browser WasmJS [CsrfTokenProvider] reading a CSRF token from `<meta name="[metaName]" content="...">`.
 *
 * Safely returns `null` when `document` or the target `<meta>` element is unavailable.
 */
class MetaTagCsrfTokenProvider(
    val metaName: String = DEFAULT_META_NAME,
    override val headerName: String = CsrfTokenProvider.DEFAULT_CSRF_HEADER,
    private val onRefresh: (suspend () -> String?)? = null,
) : CsrfTokenProvider {
    init {
        require(metaName.isNotBlank()) { "metaName must not be blank." }
        require(headerName.isNotBlank()) { "headerName must not be blank." }
    }

    override suspend fun currentToken(): String? = readMetaTagToken()

    override suspend fun refreshToken(): String? =
        if (onRefresh != null) {
            onRefresh.invoke()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            readMetaTagToken()
        }

    private fun readMetaTagToken(): String? = jsReadMetaTagContent(metaName)?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val DEFAULT_META_NAME: String = "csrf-token"
    }
}
