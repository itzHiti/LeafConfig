package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a property whose value must never appear in diagnostics or diffs, such as a password or an
 * API token.
 *
 * <p>For every diagnostic at the property or below it (nested sections, map values, list elements)
 * the code, path and source position are kept and the message is replaced by a fixed text, because
 * any adapter or validator may have quoted the value. In a {@link
 * dev.leafconfig.migration.ConfigDiff} produced by a dry run, values at or below the property are
 * replaced by the string {@code ***}.
 *
 * <p>A failing migration step is reported at the {@code config-version} path with the text of its
 * exception; that text is not redacted, so steps must not put secret values into exception
 * messages.
 *
 * <p>The value itself is loaded, validated and written to the file as usual; {@code @Secret} does
 * not encrypt anything and does not change the generated YAML.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Secret {}
