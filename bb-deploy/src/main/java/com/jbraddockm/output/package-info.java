/**
 * Parsers for the text that {@code blackberry-deploy} prints, producing typed results.
 *
 * <p>This package is the reusable part of the project. It does not run the tool and does not depend
 * on it: it only interprets lines of text, so it can be used by any tool that captures that output,
 * and it can be tested against recorded output.
 *
 * <h2>Using the parsers</h2>
 *
 * <pre>{@code
 * List<String> lines = ...; // the captured stdout of "-listInstalledApps"
 * InstalledApps apps = OutputParsers.INSTALLED_APPS.parse(lines);
 * apps.apps().forEach(app -> System.out.println(app.name() + " " + app.version()));
 * }</pre>
 *
 * <p>Every parser is an {@link com.jbraddockm.output.OutputParser}. A parser never throws for
 * output it does not understand; unrecognised lines are reported in the result instead. Whether a
 * command failed is a separate question, answered by {@link
 * com.jbraddockm.output.OutputParser#detectFailure}: the tool exits with status 0 in some failure
 * cases, so the exit status alone is not enough.
 *
 * <h2>Adding a parser</h2>
 *
 * <p>Implement {@code OutputParser<T>} for a result type implementing {@link
 * com.jbraddockm.output.CommandResult}. The implementations in this package are package-private on
 * purpose and are exposed only through {@link com.jbraddockm.output.OutputParsers}.
 */
package com.jbraddockm.output;
