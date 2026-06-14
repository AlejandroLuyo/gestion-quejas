let puntuacionSeleccionada = 0;
const estrellas = document.querySelectorAll('.estrella');

function seleccionar(n) {
    puntuacionSeleccionada = n;
    estrellas.forEach((e, i) => {
        e.classList.toggle('activa', i < n);
    });
    document.getElementById('btn-enviar').disabled = false;
}

function mostrarError(texto) {
    const mensajeError = document.getElementById('mensaje-error');
    mensajeError.textContent = texto;
    mensajeError.style.display = 'block';
}

function ocultarError() {
    document.getElementById('mensaje-error').style.display = 'none';
}

function deshabilitarFormulario() {
    document.getElementById('btn-enviar').disabled = true;
    estrellas.forEach(e => e.style.pointerEvents = 'none');
    document.getElementById('comentario').disabled = true;
}

function enviar() {
    const comentario = document.getElementById('comentario').value;
    const btn = document.getElementById('btn-enviar');

    ocultarError();
    btn.disabled = true;

    fetch('/csat/guardar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'token=' + token + '&puntuacion=' + puntuacionSeleccionada + '&comentario=' + encodeURIComponent(comentario)
    })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                document.getElementById('formulario').style.display = 'none';
                document.getElementById('gracias').style.display = 'block';
            } else if (data.status === 'ya_respondido') {
                mostrarError('Ya has respondido esta encuesta anteriormente.');
                deshabilitarFormulario();
            } else if (data.status === 'error') {
                mostrarError('Este enlace ya no es válido. Por favor solicita uno nuevo.');
                deshabilitarFormulario();
            } else {
                mostrarError('Ocurrió un error inesperado. Intenta nuevamente.');
                btn.disabled = false;
            }
        })
        .catch(() => {
            mostrarError('No se pudo conectar con el servidor. Verifica tu conexión e intenta nuevamente.');
            btn.disabled = false;
        });
}