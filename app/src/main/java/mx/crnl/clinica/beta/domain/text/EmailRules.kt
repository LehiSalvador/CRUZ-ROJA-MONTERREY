package mx.crnl.clinica.beta.domain.text

object EmailRules {
    const val MAX_LENGTH = 254
    private val PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun isValid(text: String): Boolean = text.length <= MAX_LENGTH && PATTERN.matches(text)
}
