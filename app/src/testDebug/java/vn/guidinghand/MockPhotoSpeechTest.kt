package vn.guidinghand

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MockPhotoSpeechTest {
    @Test fun developmentPhotoFlowKeepsSpeechAndRequiresRealSendEvidence() = runBlocking {
        val r = Region(.1f, .3f, .25f, .4f)
        val title = ScreenNode("com.zing.zalo:id/chat_title@0", "An", Region(.2f,.04f,.8f,.1f), false)
        val composer = ScreenNode("composer", "Nhập tin nhắn", Region(.1f,.8f,.8f,.9f), true, className="android.widget.EditText")
        val attach = ScreenNode("attach", "Photos", r, true)
        val photo = ScreenNode("photo", "Family photo", r, true, className="android.widget.ImageView")
        val send = ScreenNode("send", "Gửi", r, true)
        val before = ScreenState("com.zing.zalo", listOf(title,composer,attach))
        val gallery = ScreenState("com.zing.zalo", listOf(ScreenNode("gallery","Gallery",null,false),photo))
        val ready = ScreenState("com.zing.zalo",listOf(title,composer,photo.copy(selected=true),send))
        val delivered = ScreenState("com.zing.zalo",listOf(title,composer,
            ScreenNode("outgoing","Sent photo Family photo",null,false,className="android.widget.ImageView"),
            ScreenNode("delivery","Delivered",null,false)))
        var actual = before
        val spoken = mutableListOf<String>()
        val output = object : SpeechOutput {
            override suspend fun speak(text:String,stillSafe:suspend()->Boolean) { check(stillSafe()); spoken += text }
            override fun stop() {}
            override fun close() {}
        }
        val model = MockModel({actual},output)
        val engine = GuideEngine(model,{},allowDevStandIn=true)
        engine.startGoal("Gửi ảnh đã chọn",PhotoTask("An","Family photo"))
        engine.onScreen(actual); assertEquals(GuideStatus.WAITING_TAP,engine.status)
        engine.onTap(attach); actual=gallery; engine.onScreen(actual)
        assertEquals(GuideStatus.WAITING_TAP,engine.status)
        engine.onTap(photo); actual=ready; engine.onScreen(actual)
        assertEquals(GuideStatus.WAITING_TAP,engine.status)
        assertEquals(3,spoken.size)
        engine.onTap(send); actual=delivered; engine.onScreen(actual)
        assertEquals(GuideStatus.DONE,engine.status)
        assertTrue(engine.verifiedStepIds.contains("delivery"))
    }
}
