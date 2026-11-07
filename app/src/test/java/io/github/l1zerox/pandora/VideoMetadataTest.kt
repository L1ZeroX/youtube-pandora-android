package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class VideoMetadataTest {
    private val id="AbCd_12-345"
    @Test fun durationParsesNumericClockAndIsoFormats() {
        for((input,expected) in listOf("153.5" to 153.5,"2:33" to 153.0,"1:02:03" to 3723.0,
            "PT2M33S" to 153.0,"PT1H2M3.5S" to 3723.5,"P1DT1S" to 86401.0)) {
            assertEquals(expected,parsedDuration(input)!!,.001)
        }
        for(value in listOf(null,"","null","N/A","NaN","Infinity","-5","0","1:99","PT0S")) assertNull(parsedDuration(value))
    }
    @Test fun readsOnlyMatchingVideoDetailsAndIgnoresRelatedDurations() {
        val html="""{"lengthSeconds":"999","videoDetails":{"videoId":"other_id123","lengthSeconds":"777"},"player":{"videoDetails":{"videoId":"$id","title":"Title with } and \"quotes\"","lengthSeconds":"153"}}}"""
        assertEquals(153.0,youtubeDurationFromHtml(html,id)!!,0.0)
        assertNull(youtubeDurationFromHtml(html,"missing_123"))
    }
    @Test fun missingDetailsDurationCannotBleedIntoTheNextObject() {
        assertNull(youtubeDurationFromHtml("""{"videoDetails":{"videoId":"$id"},"related":{"lengthSeconds":"999"}}""",id))
        assertNull(youtubeDurationFromHtml("""{"videoDetails":{"videoId":"$id","lengthSeconds":"153oops"}}""",id))
    }
    @Test fun canonicalHeadMetadataIsAValidatedFallback() {
        val html="""<head><link href='https://www.youtube.com/watch?v=$id' rel='canonical'><meta content='PT2M33S' itemprop='duration'></head>"""
        assertEquals(153.0,youtubeDurationFromHtml(html,id)!!,0.0)
        assertNull(youtubeDurationFromHtml(html,"different01"))
        assertNull(youtubeDurationFromHtml("<head><meta itemprop='duration' content='PT2M33S'></head>",id))
    }
    @Test fun liveAndInvalidDurationsStayUnknown() {
        assertNull(youtubeDurationFromHtml("""{"videoDetails":{"videoId":"$id","isLive":true,"lengthSeconds":"900"}}""",id))
        assertNull(youtubeDurationFromHtml("""{"videoDetails":{"videoId":"$id","lengthSeconds":"0"}}""",id))
        assertNull(youtubeDurationFromHtml("<html>Consent required</html>",id))
    }
    @Test fun refreshReplacesMissingMetadataAndPreservesQueueState() {
        val old=Video("shared:youtube:$id","https://youtu.be/$id","Old title",selected=false,status="完了",savedUri="content://saved",error="existing",resolved=false)
        val fresh=Video("youtube:$id","https://youtube.com/watch?v=$id","New title","Youtube",153.0,"https://image")
        val result=mergeDiscoveredVideos(listOf(old),listOf(fresh)).single()
        assertEquals(old.copy(title=fresh.title,site=fresh.site,duration=153.0,thumbnail=fresh.thumbnail,resolved=true),result)
    }
    @Test fun incompleteRefreshCannotEraseKnownDurationOrDuplicateQueueEntries() {
        val old=Video("a","https://youtu.be/$id","Title",duration=153.0,thumbnail="https://image")
        val fresh=old.copy(key="different",duration=null,thumbnail="",title="タイトル不明")
        val other=Video("b","https://example.com/b","B")
        val result=mergeDiscoveredVideos(listOf(old),listOf(fresh,other,other))
        assertEquals(listOf(old,other),result)
    }
}
