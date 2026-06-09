let puntuacionSeleccionada = 0;
const estrellas = document.querySelectorAll('.estrella');

function seleccionar(n) {
    puntuacionSeleccionada = n;
    estrellas.forEach((e, i) => {
        e.classList.toggle('activa', i < n);
    });
    document.getElementById('btn-enviar').disabled = false;
}

function enviar() {
    const comentario = document.getElementById('comentario').value;
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
            }
        });
}