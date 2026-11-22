# RAF Capsule OS

<p align="center">
  <strong>🌐 Idioma:</strong>
  <a href="README.md">English</a> •
  <a href="README-in.md">Bahasa Indonesia</a> •
  <a href="README-ja.md">日本語</a> •
  <a href="README-ko.md">한국어</a> •
  <a href="README-zh.md">简体中文</a> •
  <a href="README-es.md"><strong>Español</strong></a>
</p>

<p align="center">
  <strong>Cápsula Dinámica de Cámara Frontal y Superposición Interactiva para Android 13–17</strong><br/>
  Kotlin 2.x • Jetpack Compose • Material 3 Expressive • Navigation 3 • Shaders AGSL • Shizuku • Clean Architecture
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v1.3.1-1976D2" alt="Versión v1.3.1" />
  <img src="https://img.shields.io/badge/Min%20SDK-33%20(Android%2013)-3DDC84?logo=android&logoColor=white" alt="Min SDK 33" />
  <img src="https://img.shields.io/badge/Target%20SDK-37%20(Android%2017)-0078D7?logo=android&logoColor=white" alt="Target SDK 37" />
  <img src="https://img.shields.io/badge/Languages-14%20Idiomas-9C27B0" alt="14 Idiomas" />
</p>

**RAF Capsule OS** transforma el recorte de la cámara frontal en una cápsula interactiva alineada 1:1 en **modo Vertical y Horizontal**, impulsada por física de resortes sincronizada con VSYNC, shaders AGSL de brillo neuronal, háptica continua ultrasuave y un coordinador de estado en Kotlin puro.

---

## Galería Visual

<table>
  <tr>
    <td align="center" width="50%">
      <img src="docs/media/media-player.gif" alt="Reproductor Multimedia" width="100%" /><br/>
      <strong>Reproductor Multimedia y Reloj en Vivo</strong>
    </td>
    <td align="center" width="50%">
      <img src="docs/media/separated-bubble.gif" alt="Burbuja de Estado Separada" width="100%" /><br/>
      <strong>Burbuja de Estado Separada y Fusión Líquida</strong>
    </td>
  </tr>
  <tr>
    <td align="center" colspan="2">
      <img src="docs/media/volume-mixer.gif" alt="Mezclador de Volumen" width="50%" /><br/>
      <strong>Nuevo Mezclador de Volumen M3 Expressive</strong>
    </td>
  </tr>
</table>

### Funciones en Modo Horizontal

| Función | Comportamiento Horizontal |
|---|---|
| **Píldora Lateral Vertical** | Se ancla verticalmente junto a la cámara rotada con iconos y datos compactos |
| **Burbuja de Estado Vertical** | Sigue el lateral de la cámara con puntos verticales y deslizamiento vertical |
| **Tarjetas de Altura Completa** | Altura completa con margen simétrico, desenfoque horizontal y excepción cuadrada en Linterna |
| **Gestos Horizontales Naturales** | Desliza horizontalmente para expandir/contraer y verticalmente para cambiar de tarjeta o sesión |
| **Barra Lateral de Cámara** | Muestra icono/nombre de la app y reloj en vivo con segundos + fecha clicable al Calendario |

---

## Características Principales

- **Cápsula Dinámica de 3 Estados (Vertical y Horizontal)**: Anillo de batería 1:1, píldora compacta (horizontal en vertical / vertical en horizontal) y tarjeta expandida esmerilada con redondeo personalizable (`0–56 dp` o detección automática).
- **Modo Horizontal Completo**: Sigue la cámara rotada con píldora lateral vertical, burbuja de estado separada con puntos verticales, tarjetas de altura completa, gesto horizontal para expandir/contraer y gesto vertical para cambiar tarjetas.
- **Barra Lateral de Cámara**: Muestra el icono y nombre de la app junto a la cámara, más un reloj en vivo con segundos y la fecha completa con acceso directo al Calendario.
- **Burbuja de Estado Separada**: Hasta 9 indicadores del sistema (Linterna, Zona Wi-Fi, Actividad en vivo, Modo avión, No molestar, Ahorro de batería, VPN, Silencio/Vibración y Ahorro de datos).
- **Multimedia, Letras LRC y Nuevo Mezclador de Volumen**: Carrusel multisesión, letras sincronizadas, selector de salida de audio y tarjetas de volumen con silencio al tocar, porcentaje en vivo y barra segmentada Sonido/Vibración/Silencio.
- **Centro de Permisos + Shizuku + Compatibilidad Xiaomi HyperOS**: Concesión de `WRITE_SECURE_SETTINGS` en un toque con **Shizuku** (sin PC), guía **ADB** copiable y protecciones + accesos directos de Inicio automático/Batería/Ventanas emergentes exclusivos para **Xiaomi / Redmi / POCO** (cero cambios en Pixel).

---

## Instalación y Permisos

1. Descarga el último `app-release.apk` desde [GitHub Releases](https://github.com/RidhwanAF/RAF-Capsule/releases/latest) e instálalo (`adb install -r app-release.apk` recomendado).
2. Abre **RAF Capsule OS → Configurar permisos** y concede Accesibilidad y Acceso a notificaciones.
3. Para conceder `WRITE_SECURE_SETTINGS`:
   - **Shizuku (Sin PC)**: Inicia [Shizuku](https://shizuku.rikka.app/) con Depuración inalámbrica y toca **Conceder con Shizuku**.
   - **ADB**:
     ```bash
     adb shell pm grant com.raf.capsuleos android.permission.WRITE_SECURE_SETTINGS
     ```
