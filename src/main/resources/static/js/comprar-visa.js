const precios = { standard: 80, rush: 120, super_rush: 150 };

function mostrarPaso(n) {
    document.querySelectorAll('.wizard-part').forEach(p => {
        p.classList.toggle('active', p.dataset.part === String(n));
    });
    document.querySelectorAll('.wizard-step-indicator').forEach(s => {
        const step = Number(s.dataset.step);
        s.classList.toggle('active', step === n);
        s.classList.toggle('completed', step < n);
    });
}

function validarPaso(n) {
    const part = document.querySelector('.wizard-part[data-part="' + n + '"]');
    const campos = part.querySelectorAll('[required]');
    for (const campo of campos) {
        if (!campo.value) {
            campo.reportValidity();
            return false;
        }
    }
    return true;
}

function siguientePaso(actual) {
    if (!validarPaso(actual)) return;
    mostrarPaso(actual + 1);
}

function pasoAnterior(actual) {
    mostrarPaso(actual - 1);
}

function actualizarResumen() {
    const seleccionado = document.querySelector('input[name="processingSpeed"]:checked');
    const precio = precios[seleccionado.value] || 0;
    document.getElementById('resumen-precio').textContent = 'S/ ' + precio.toFixed(2);
}