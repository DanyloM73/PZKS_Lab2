package com.danylom73.cli

import com.danylom73.analysis.AnalysisError
import com.danylom73.analysis.ArithmeticExpressionAnalyzer
import com.danylom73.analysis.ErrorCategory
import com.danylom73.analysis.SupportedFunctions
import com.danylom73.expression.ExpressionFormatter
import com.danylom73.expression.ExpressionParser
import com.danylom73.parallel.ParallelExpressionOptimizer
import com.danylom73.parallel.ParallelPlan
import com.danylom73.semantic.SemanticChecker

fun main(args: Array<String>) {
    val analyzer = ArithmeticExpressionAnalyzer()
    val optimizer = ParallelExpressionOptimizer()

    if (args.isNotEmpty()) {
        val input = args.joinToString(" ")
        if (input.trim().equals(":functions", ignoreCase = true)) {
            printSupportedFunctions()
        } else {
            printResult(input, analyzer, optimizer)
        }
        return
    }

    println("\n${TerminalColors.bold(TerminalColors.cyan("Паралельна форма арифметичного виразу"))}")
    println("Введіть вираз; :functions — перелік функцій, :exit — вихід.")
    while (true) {
        print("\n> ")
        val expression = readlnOrNull() ?: break
        when {
            expression.trim().equals(":exit", ignoreCase = true) -> break
            expression.trim().equals(":functions", ignoreCase = true) -> printSupportedFunctions()
            else -> printResult(expression, analyzer, optimizer)
        }
    }
}

private fun printResult(
    expression: String,
    analyzer: ArithmeticExpressionAnalyzer,
    optimizer: ParallelExpressionOptimizer,
) {
    val result = analyzer.analyze(expression)
    if (!result.isValid) {
        printErrors(expression, result.errors)
        return
    }

    val parser = ExpressionParser(result.tokens)
    val original = parser.parse()
    val semanticErrors = SemanticChecker.check(expression, original, parser.sourceSpans)
    if (semanticErrors.isNotEmpty()) {
        printErrors(expression, semanticErrors)
        return
    }

    val optimization = optimizer.optimizeWithTrace(original)
    val optimized = optimization.optimized
    val before = ParallelPlan.from(original)
    val after = ParallelPlan.from(optimized)

    println(TerminalColors.green("Вираз коректний."))
    println("\nПокрокова оптимізація:")
    println(optimization.renderSteps())
    println()
    println("Паралельна форма: ${ExpressionFormatter.format(optimized)}")
    println("Операцій: ${before.operations.size} → ${after.operations.size}; ярусів: ${before.depth} → ${after.depth}; " +
        "максимальна ширина: ${before.maximumWidth} → ${after.maximumWidth}.")
    println("\nДерево паралельної форми:")
    if (after.operations.isNotEmpty()) {
        println("Угорі операнди; рядок «ярус N» містить операції, які виконуються одночасно на N-му кроці.")
    }
    println(after.renderTree())
    println("\nНайраніший розклад виконання за ярусами:")
    println(after.renderSchedule())
}

private fun printErrors(expression: String, errors: List<AnalysisError>) {
    println(TerminalColors.red("Знайдено помилок: ${errors.size}"))
    println(ErrorHighlighter.format(expression, errors))
    errors.forEachIndexed { index, error ->
        val category = "[${error.category.displayName}]"
        val coloredCategory = when (error.category) {
            ErrorCategory.LEXICAL -> TerminalColors.yellow(category)
            ErrorCategory.SYNTACTIC, ErrorCategory.SEMANTIC -> TerminalColors.red(category)
        }
        println("${index + 1}. $coloredCategory ${error.message} (${error.line}:${error.column})")
    }
}

private fun printSupportedFunctions() {
    println(TerminalColors.bold(TerminalColors.cyan("Доступні функції:")))
    SupportedFunctions.all().forEach { function ->
        val parameters = if (function.argumentCount == 1) "x" else "x, y"
        val argumentWord = if (function.argumentCount == 1) "аргумент" else "аргументи"
        println("  ${TerminalColors.green("${function.name}($parameters)")} — " +
            "${function.argumentCount} $argumentWord")
    }
    println("Назви функцій чутливі до регістру.")
}
