package org.scottishtecharmy.soundscape.platform

/**
 * The name of the language [tag] describes, in the language the app is running
 * in - "en-GB" becomes "British English" for an English user and "Anglais
 * britannique" for a French one.
 *
 * Used for the headings and ordering of the voice picker's language sections.
 * Falls back to the tag itself where the platform has no name for it, which is
 * still more use to a user than an empty heading.
 */
expect fun localizedLanguageName(tag: String): String
