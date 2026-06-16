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

function verificarOrden() {
    const orderId = document.getElementById('order-id-input').value.trim();
    const errorDiv = document.getElementById('orden-error');
    errorDiv.style.display = 'none';

    if (!orderId) {
        errorDiv.textContent = 'Ingresa un número de orden.';
        errorDiv.style.display = 'block';
        return;
    }

    fetch('/admin/portal-cliente/verificar-orden?orderId=' + encodeURIComponent(orderId))
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                document.getElementById('confirmacion-nombre').textContent = data.nombreCliente;
                document.getElementById('confirmacion-email').textContent = data.emailCliente;
                mostrarPaso(2);
            } else {
                errorDiv.textContent = 'No se encontró ninguna orden con ese número.';
                errorDiv.style.display = 'block';
            }
        })
        .catch(() => {
            errorDiv.textContent = 'Error al verificar la orden. Intenta nuevamente.';
            errorDiv.style.display = 'block';
        });
}