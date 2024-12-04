package com.dominions.modmerger.constants

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

class RegexWrapper private constructor(pattern: String) {
    private val regex = Regex(pattern)
    private val compiledPattern = Pattern.compile(pattern)
    private val predicate = compiledPattern.asPredicate()

    // Thread-local matcher to prevent constant allocation
    private val threadLocalMatcher = ThreadLocal.withInitial {
        compiledPattern.matcher("")
    }

    fun matches(input: String): Boolean = predicate.test(input)

    fun find(input: String): MatchResult? = regex.find(input)

    fun getMatcher(input: String): Matcher {
        val matcher = threadLocalMatcher.get()
        return matcher.reset(input)
    }

    // Delegate to original Regex for compatibility
    fun toRegex(): Regex = regex

    fun getPatternName(): String {
        return patternNameMap[regex.pattern] ?: regex.pattern
    }

    companion object {
        private val cache = ConcurrentHashMap<String, RegexWrapper>()

        private val patternNameMap = ModPatterns::class.java.declaredFields
            .mapNotNull { field ->
                field.isAccessible = true
                val regex = field.get(ModPatterns) as? Regex
                regex?.let {
                    it.pattern to field.name
                }
            }
            .toMap()

        fun of(pattern: String): RegexWrapper {
            return cache.computeIfAbsent(pattern) { RegexWrapper(it) }
        }

        // Create from existing Regex for backward compatibility
        fun fromRegex(regex: Regex): RegexWrapper {
            return of(regex.pattern)
        }
    }
}