/**
 * Reading BlackBerry archives ({@code .bar} files).
 *
 * <p>A bar is a ZIP archive whose {@code META-INF/MANIFEST.MF} describes the package: its kind, id,
 * name and version. {@link BarInspector} reads that manifest, and nothing else of the archive, with
 * BlackBerry's own library, which is an external dependency of the build. The library's types stay
 * inside this package; what leaves it are the records of this package, so the rest of the code does
 * not depend on the library.
 */
package com.jbraddockm.core.bar;
