package com.cemail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cemail.app.ui.glass.GlassBackground
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.theme.CemailTheme

// Marcador de posición: comprueba que el tema "Ce" funciona. Aquí irá la navegación de la app.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as CemailApplication).settings
        setContent {
            val settings by repo.state.collectAsStateWithLifecycle()
            CemailTheme(settings) {
                GlassBackground {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.app_name),
                            Modifier
                                .glass(RoundedCornerShape(28.dp), GlassKind.Card, elevation = 6.dp)
                                .padding(horizontal = 36.dp, vertical = 20.dp),
                        )
                    }
                }
            }
        }
    }
}
