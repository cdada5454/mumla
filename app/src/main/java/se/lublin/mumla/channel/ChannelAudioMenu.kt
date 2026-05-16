package se.lublin.mumla.channel

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import se.lublin.humla.IHumlaService
import se.lublin.humla.IHumlaSession
import se.lublin.mumla.R
import se.lublin.mumla.Settings

object ChannelAudioMenu {
    fun inflate(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.channel_menu, menu)
    }

    fun prepare(context: Context, service: IHumlaService?, menu: Menu) {
        val muteItem = menu.findItem(R.id.menu_mute_button) ?: return
        val deafenItem = menu.findItem(R.id.menu_deafen_button) ?: return
        if (service == null || !service.isConnected) {
            muteItem.isVisible = false
            deafenItem.isVisible = false
            return
        }

        muteItem.isVisible = true
        deafenItem.isVisible = true

        val self = try {
            val session: IHumlaSession = service.HumlaSession()
            session.sessionUser ?: return
        } catch (_: IllegalStateException) {
            muteItem.isVisible = false
            deafenItem.isVisible = false
            return
        }
        muteItem.setIcon(if (self.isSelfMuted) R.drawable.mic_off_24 else R.drawable.ic_action_microphone)
        deafenItem.setIcon(if (self.isSelfDeafened) R.drawable.ic_action_audio_muted else R.drawable.ic_action_audio)

        muteItem.icon?.mutate()?.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
        deafenItem.icon?.mutate()?.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
    }

    fun handle(context: Context, service: IHumlaService?, item: MenuItem): Boolean {
        val settings = Settings.getInstance(context)
        return when (item.itemId) {
            R.id.menu_mute_button -> {
                val (session, self) = try {
                    val currentSession = service?.takeIf { it.isConnected }?.HumlaSession() ?: return false
                    currentSession to (currentSession.sessionUser ?: return false)
                } catch (_: IllegalStateException) {
                    return false
                }
                val muted = !self.isSelfMuted
                val deafened = self.isSelfDeafened && muted
                session.setSelfMuteDeafState(muted, deafened)
                settings.setMutedAndDeafened(muted, deafened)
                true
            }

            R.id.menu_deafen_button -> {
                val (session, self) = try {
                    val currentSession = service?.takeIf { it.isConnected }?.HumlaSession() ?: return false
                    currentSession to (currentSession.sessionUser ?: return false)
                } catch (_: IllegalStateException) {
                    return false
                }
                val deafened = !self.isSelfDeafened
                session.setSelfMuteDeafState(deafened, deafened)
                settings.setMutedAndDeafened(deafened, deafened)
                true
            }

            R.id.menu_input_voice -> {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_VOICE)
                true
            }

            R.id.menu_input_ptt -> {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT)
                true
            }

            R.id.menu_input_continuous -> {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_CONTINUOUS)
                true
            }

            else -> false
        }
    }
}
