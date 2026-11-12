package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.JanelasDoSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quem está na frente da despedida, lido do `dumpsys window windows | grep -E <FILTRO>`. */
class JanelasDoSistemaTest {

    private val pacote = "br.com.hugolumazzini.havaltrip"
    private val nossa = "HavalTripDespedida"

    // Linhas reais do emulador (Android 12), com o título da nossa janela trocado
    // para o que o app passa a usar. O "MapaDeTeste" é um app de teste que imita
    // o mapa do AutoPanel e foi colocado depois da despedida.
    private val android12 = """
  Window #10 Window{4ca8e80 u0 com.android.systemui.ImageWallpaper}:
    mDisplayId=0 mSession=Session{a1 706:u0a10135} mClient=android.os.BinderProxy@1
    mAttrs={(0,0)(2880x1290) gr=TOP START CENTER ty=WALLPAPER fmt=RGBX_8888
    mBaseLayer=11000 mSubLayer=0    mToken=WallpaperWindowToken{x}
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
  Window #11 Window{94c441c u0 MapaDeTeste}:
    mDisplayId=3 mSession=Session{25f88f0 5199:u0a10147} mClient=android.os.BinderProxy@d4ba0ee
    mAttrs={(0,0)(768x503) gr=CENTER sim={adjust=pan} ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mBaseLayer=121000 mSubLayer=0    mToken=WindowToken{a50398f android.os.BinderProxy@d4ba0ee}
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
  Window #12 Window{79b517e u0 HavalTripDespedida}:
    mDisplayId=3 mSession=Session{3bfd7c2 5081:u0a10198} mClient=android.os.BinderProxy@e700300
    mAttrs={(0,0)(fillxfill) gr=TOP START CENTER sim={adjust=pan} layoutInDisplayCutoutMode=shortEdges ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mBaseLayer=121000 mSubLayer=0    mToken=WindowToken{89b7739 android.os.BinderProxy@e700300}
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
  Window #13 Window{a134c9 u0 ShellDropTarget}:
    mDisplayId=3 mSession=Session{ce8f249 706:u0a10135} mClient=android.os.BinderProxy@ac6fcd0
    mAttrs={(0,0)(fillxfill) sim={adjust=pan} layoutInDisplayCutoutMode=always ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mBaseLayer=121000 mSubLayer=0    mToken=WindowToken{8eda6ef android.os.BinderProxy@95c5493}
    mViewVisibility=0x4 mHaveFrame=true mObscured=false
  Window #14 Window{9d42c09 u0 br.com.hugolumazzini.havaltrip/br.com.hugolumazzini.havaltrip.ClusterMenuActivity}:
    mDisplayId=3 rootTaskId=657 mSession=Session{3bfd7c2 5081:u0a10198} mClient=android.os.BinderProxy@21a4910
    mAttrs={(0,0)(fillxfill) sim={adjust=pan forwardNavigation} ty=BASE_APPLICATION fmt=TRANSPARENT wanim=0x10302fe
    mBaseLayer=21000 mSubLayer=0    mToken=ActivityRecord{66e315e u0 br.com.hugolumazzini.havaltrip/.ClusterMenuActivity t657}
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
    """.trimIndent()

    @Test
    fun `le dono, tela, tipo, camada e visibilidade de cada janela`() {
        val janelas = JanelasDoSistema.listar(android12)
        assertEquals(5, janelas.size)
        val mapa = janelas[1]
        assertEquals("MapaDeTeste", mapa.dono)
        assertEquals(3, mapa.tela)
        assertEquals("APPLICATION_OVERLAY", mapa.tipo)
        assertEquals(121000, mapa.camada)
        assertTrue(mapa.visivel)
        assertFalse("a ShellDropTarget está invisível (0x4)", janelas[3].visivel)
        assertEquals("BASE_APPLICATION", janelas[4].tipo)
    }

    @Test
    fun `aponta a janela de outro app que ficou na frente da despedida`() {
        val naFrente = JanelasDoSistema.naFrenteDaNossa(JanelasDoSistema.listar(android12), 3, nossa, pacote)
        assertEquals(listOf("MapaDeTeste"), naFrente?.map { it.dono })
    }

    @Test
    fun `com a despedida no topo, ninguem esta na frente`() {
        val noTopo = android12.replace("u0 MapaDeTeste}", "u0 TMP}")
            .replace("u0 HavalTripDespedida}", "u0 MapaDeTeste}")
            .replace("u0 TMP}", "u0 HavalTripDespedida}")
        assertEquals(emptyList<String>(), JanelasDoSistema.naFrenteDaNossa(JanelasDoSistema.listar(noTopo), 3, nossa, pacote)?.map { it.dono })
    }

    @Test
    fun `janela invisivel e janelas de outras telas nao contam`() {
        val so = """
  Window #1 Window{1 u0 Fantasma}:
    mDisplayId=3 mSession=Session{x}
    mAttrs={(0,0)(fillxfill) ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mViewVisibility=0x8 mHaveFrame=true
  Window #2 Window{2 u0 OutraTela}:
    mDisplayId=0 mSession=Session{x}
    mAttrs={(0,0)(fillxfill) ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mViewVisibility=0x0 mHaveFrame=true
  Window #3 Window{3 u0 HavalTripDespedida}:
    mDisplayId=3 mSession=Session{x}
    mAttrs={(0,0)(fillxfill) ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mViewVisibility=0x0 mHaveFrame=true
        """.trimIndent()
        assertEquals(emptyList<String>(), JanelasDoSistema.naFrenteDaNossa(JanelasDoSistema.listar(so), 3, nossa, pacote)?.map { it.dono })
    }

    // Montado a partir da lista que o carro registrou em 11/11 19:44:36 (mesmos
    // donos, na mesma ordem). Os tipos e camadas são suposição: o histórico da
    // v1.0.32 não os gravava.
    @Test
    fun `no formato do carro, o AutoPanel e o multidisplay aparecem na frente`() {
        val carro = """
  Window #20 Window{e2cd57e u0 dev.leonardovin.havalbottombar}:
    mDisplayId=3 stackId=0 mSession=Session{x}
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mBaseLayer=121000 mSubLayer=0 mAnimLayer=121000+0=121000
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
  Window #21 Window{38087bb u0 com.beantechs.multidisplay}:
    mDisplayId=3 stackId=0 mSession=Session{x}
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=SYSTEM_OVERLAY fmt=TRANSLUCENT
    mBaseLayer=221000 mSubLayer=0 mAnimLayer=221000+0=221000
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
  Window #22 Window{cafdc08 u0 HavalTripDespedida}:
    mDisplayId=3 stackId=0 mSession=Session{x}
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
    mBaseLayer=121000 mSubLayer=0 mAnimLayer=121000+0=121000
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
  Window #23 Window{bc97a6e u0 br.com.hugolumazzini.havaltrip/br.com.hugolumazzini.havaltrip.ClusterMenuActivity}:
    mDisplayId=3 stackId=5 mSession=Session{x}
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=BASE_APPLICATION fmt=TRANSPARENT
    mBaseLayer=21000 mSubLayer=0 mAnimLayer=21000+0=21000
    mViewVisibility=0x0 mHaveFrame=true mObscured=false
        """.trimIndent()
        val naFrente = JanelasDoSistema.naFrenteDaNossa(JanelasDoSistema.listar(carro), 3, nossa, pacote)
        assertEquals(listOf("dev.leonardovin.havalbottombar", "com.beantechs.multidisplay"), naFrente?.map { it.dono })
        assertEquals("dev.leonardovin.havalbottombar [APPLICATION_OVERLAY, camada 121000]", naFrente?.first()?.descrever())
    }

    @Test
    fun `sem a nossa janela na lista, a resposta e nao sei`() {
        val sem = android12.replace("u0 HavalTripDespedida}", "u0 OutraCoisa}")
        assertNull(JanelasDoSistema.naFrenteDaNossa(JanelasDoSistema.listar(sem), 3, nossa, pacote))
        assertNull(JanelasDoSistema.naFrenteDaNossa(JanelasDoSistema.listar("Permission Denial: can't dump"), 3, nossa, pacote))
    }
}
