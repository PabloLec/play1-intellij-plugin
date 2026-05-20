package com.github.pablolec.play1toolkit.templates.util

object PlayTemplatePatterns {
    val GROOVY_EXPR_BLOCK = Regex("""\$\{([^}]*)\}""")

    val TAG_EXTENDS = Regex("""#\{extends\s+['"]([^'"]+)['"]\s*/\}""")
    val TAG_INCLUDE = Regex("""#\{include\s+['"]([^'"]+)['"]\s*/\}""")

    val TAG_OPEN = Regex("""#\{(\w[\w.]*)\s*(?:[^}]*)?\}""")
    val TAG_SELF_CLOSE = Regex("""#\{(\w[\w.]*)\s*(?:[^}]*)?\s*/\}""")
    val TAG_CLOSE = Regex("""#\{/(\w[\w.]*)\}""")

    val TAG_NAME_AT = Regex("""#\{(\w[\w.]*)""")

    val GROOVY_EXPR = Regex("""\$\{(\w+)(?:[.\[(\s][^}]*)?\}""")

    val REVERSE_ROUTE = Regex("""@@?\{([A-Z]\w*(?:\.\w+)*)\(([^)]*)\)\}""")

    val STATIC_ASSET = Regex("""@\{['"]([^'"]*)['"]\}""")

    val LIST_TAG_VAR = Regex("""#\{list\s+[^}]*as\s*:\s*['"](\w+)['"]""")
    val LIST_TAG_ITEMS_AND_VAR = Regex("""#\{list\s+[^}]*items\s*:\s*([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)[^}]*as\s*:\s*['"](\w+)['"]""")

    val TAG_PARAM = Regex("""\$\{_(\w+)\}""")

    val BUILTIN_TAGS = setOf(
        "extends", "include", "doLayout", "get", "set",
        "list", "if", "else", "elseif", "ifnot",
        "form", "field", "option", "select", "checkbox",
        "textarea", "input", "label", "error", "errorClass",
        "ifError", "errors", "password",
        "url", "asset", "link", "script", "stylesheet",
        "cache", "render", "verbatim", "debug",
        "secure", "csrfToken", "csrfCheck",
        "ifConnected", "ifNotConnected",
        "a"
    )

    val BUILTIN_TAG_DOCS = mapOf(
        "extends" to "Declares a parent layout template.",
        "include" to "Includes another template inline.",
        "doLayout" to "Inserts the child template body into a layout.",
        "get" to "Outputs a variable set with #{set}.",
        "set" to "Sets a variable for use in the layout via #{get}.",
        "list" to "Iterates over a collection. Provides item, item_index, item_parity, item_isFirst, item_isLast.",
        "if" to "Conditional block.",
        "else" to "Else branch for #{if}.",
        "elseif" to "Else-if branch.",
        "ifnot" to "Negated conditional block.",
        "form" to "Generates an HTML form with reverse-route action.",
        "field" to "Renders a form field with label and error display.",
        "option" to "Renders an <option> element.",
        "select" to "Renders a <select> element.",
        "checkbox" to "Renders a checkbox input.",
        "textarea" to "Renders a textarea input.",
        "input" to "Renders an input element.",
        "label" to "Renders a form label.",
        "error" to "Displays a validation error for a field.",
        "errorClass" to "Outputs an error CSS class if field has errors.",
        "ifError" to "Conditional block shown when a field has errors.",
        "errors" to "Iterates over all validation errors.",
        "password" to "Renders a password input.",
        "url" to "Generates an absolute URL for a route.",
        "asset" to "Generates a URL for a static asset under public/.",
        "link" to "Generates an anchor tag using a reverse route.",
        "script" to "Includes a JavaScript file from public/javascripts/.",
        "stylesheet" to "Includes a CSS file from public/stylesheets/.",
        "cache" to "Caches the tag body for a given duration.",
        "render" to "Renders another template, passing variables.",
        "verbatim" to "Outputs template content literally without evaluation.",
        "debug" to "Displays debugging information.",
        "secure" to "Checks security access (Secure module).",
        "csrfToken" to "Renders a CSRF token hidden field.",
        "csrfCheck" to "Validates the CSRF token on form submission.",
        "ifConnected" to "Block shown only when user is connected (Secure module).",
        "ifNotConnected" to "Block shown only when user is not connected.",
        "a" to "Generates an anchor tag for a reverse route."
    )
}
