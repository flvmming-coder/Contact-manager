package com.example.contactmanagerdemo.core

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val NETWORK_TIMEOUT_MS = 8_000
    private const val GITHUB_RELEASES_URL =
        "https://api.github.com/repos/flvmming-coder/Contact-manager/releases?per_page=20"

    data class ReleaseInfo(
        val tagName: String,
        val htmlUrl: String,
        val downloadUrl: String,
        val isPrerelease: Boolean,
    )

    fun fetchLatestReleaseOrPrerelease(): ReleaseInfo {
        val url = URL(GITHUB_RELEASES_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("GitHub API response code=$responseCode")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseLatestRelease(JSONArray(body))
                ?: throw IllegalStateException("No releases found")
        } finally {
            connection.disconnect()
        }
    }

    fun normalizeVersionTag(rawVersion: String): String {
        return rawVersion.trim().removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
    }

    fun compareVersions(left: String, right: String): Int {
        val leftParts = parseVersionParts(left)
        val rightParts = parseVersionParts(right)
        val maxLen = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until maxLen) {
            val a = leftParts.getOrElse(i) { 0 }
            val b = rightParts.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }

    private fun parseLatestRelease(array: JSONArray): ReleaseInfo? {
        var best: ReleaseInfo? = null
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            val tagName = obj.optString("tag_name", "").trim()
            if (tagName.isBlank()) continue

            val candidate = ReleaseInfo(
                tagName = tagName,
                htmlUrl = obj.optString("html_url", "").trim(),
                downloadUrl = extractApkDownloadUrl(obj),
                isPrerelease = obj.optBoolean("prerelease", false),
            )

            val currentBest = best
            if (currentBest == null) {
                best = candidate
                continue
            }

            val cmp = compareVersions(
                normalizeVersionTag(candidate.tagName),
                normalizeVersionTag(currentBest.tagName),
            )
            if (cmp > 0 || (cmp == 0 && !candidate.isPrerelease && currentBest.isPrerelease)) {
                best = candidate
            }
        }
        return best
    }

    private fun extractApkDownloadUrl(releaseObj: org.json.JSONObject): String {
        val assets = releaseObj.optJSONArray("assets") ?: return ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val url = asset.optString("browser_download_url", "").trim()
            if (url.isBlank()) continue
            val name = asset.optString("name", "").lowercase()
            if (name.endsWith(".apk")) return url
        }
        return ""
    }

    private fun parseVersionParts(version: String): List<Int> {
        return version.split(".").map { part ->
            part.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0
        }
    }
}
