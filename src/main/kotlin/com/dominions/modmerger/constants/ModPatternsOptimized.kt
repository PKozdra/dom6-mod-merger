package com.dominions.modmerger.constants

import java.util.concurrent.ConcurrentHashMap

object ModPatternsOptimized {
    private val wrapperCache = ConcurrentHashMap<Regex, RegexWrapper>()

    // Extension function to get optimized wrapper
    private fun Regex.optimized(): RegexWrapper {
        return wrapperCache.computeIfAbsent(this) { RegexWrapper.fromRegex(it) }
    }

    // Wrapper class that maintains the same interface but uses optimized patterns
    class OptimizedPattern(private val pattern: Regex) {
        private val wrapper = pattern.optimized()

        fun matches(input: String): Boolean = wrapper.matches(input)
        fun find(input: String): MatchResult? = wrapper.find(input)

        // For complete backward compatibility
        fun toRegex(): Regex = pattern
    }

    // Function to convert existing ModPatterns to optimized versions
    fun wrapPatterns(patterns: Map<*, List<Regex>>): Map<*, List<OptimizedPattern>> {
        return patterns.mapValues { (_, patternList) ->
            patternList.map { OptimizedPattern(it) }
        }
    }
}
