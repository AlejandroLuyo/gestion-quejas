let usuarioIdActual = null;

function abrirPanelUsuario(btn) {
    usuarioIdActual = btn.dataset.id;
    const nombre = btn.dataset.nombre;
    const email  = btn.dataset.email;
    const rol    = btn.dataset.rol;

    document.getElementById('panelSub').textContent      = nombre + ' · ' + rol;
    document.getElementById('panelNombre').value         = nombre;
    document.getElementById('panelEmail').value          = email;
    document.getElementById('panelPassword').value       = '';
    document.getElementById('panelRol').value            = rol;
    document.getElementById('panelError').style.display  = 'none';

    document.getElementById('usuarioOverlay').classList.add('activo');
    document.getElementById('usuarioPanel').classList.add('activo');
}

function cerrarPanelUsuario() {
    document.getElementById('usuarioOverlay').classList.remove('activo');
    document.getElementById('usuarioPanel').classList.remove('activo');
    usuarioIdActual = null;
}

function guardarUsuario() {
    const email    = document.getElementById('panelEmail').value.trim();
    const password = document.getElementById('panelPassword').value;
    const rol      = document.getElementById('panelRol').value;
    const errorDiv = document.getElementById('panelError');
    const btnGuardar = document.getElementById('btnGuardarUsuario');

    errorDiv.style.display = 'none';

    if (!email) {
        errorDiv.textContent = 'El email no puede estar vacío.';
        errorDiv.style.display = 'block';
        return;
    }
    if (password && password.length < 6) {
        errorDiv.textContent = 'La contraseña debe tener al menos 6 caracteres.';
        errorDiv.style.display = 'block';
        return;
    }

    if (!confirm('¿Estás seguro de realizar este cambio?')) return;

    btnGuardar.disabled = true;
    btnGuardar.textContent = 'Guardando...';

    const params = new URLSearchParams();
    params.append('email', email);
    params.append('rol', rol);
    if (password) params.append('password', password);

    fetch('/admin/usuarios/' + usuarioIdActual + '/editar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString()
    })
        .then(res => {
            if (res.ok) {
                cerrarPanelUsuario();
                location.reload();
            } else {
                errorDiv.textContent = 'Ocurrió un error al guardar. Intenta nuevamente.';
                errorDiv.style.display = 'block';
                btnGuardar.disabled = false;
                btnGuardar.textContent = 'Guardar cambios';
            }
        })
        .catch(() => {
            errorDiv.textContent = 'Error de conexión. Intenta nuevamente.';
            errorDiv.style.display = 'block';
            btnGuardar.disabled = false;
            btnGuardar.textContent = 'Guardar cambios';
        });
}

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.btn-editar-usuario').forEach(function (btn) {
        btn.addEventListener('click', function () {
            abrirPanelUsuario(this);
        });
    });

    // Búsqueda y filtros
    const busqueda = document.getElementById('usuariosBusqueda');
    const filtroRol = document.getElementById('filtroRol');
    const filtroEstado = document.getElementById('filtroEstado');

    function filtrarUsuarios() {
        const texto = busqueda.value.toLowerCase().trim();
        const rol   = filtroRol.value;
        const estado = filtroEstado.value;

        document.querySelectorAll('tbody tr').forEach(function (fila) {
            const nombre  = fila.cells[0].textContent.toLowerCase();
            const email   = fila.cells[1].textContent.toLowerCase();
            const filRol  = fila.cells[2].textContent.trim();
            const filEst  = fila.cells[3].textContent.trim().toLowerCase();

            const coincideTexto  = !texto  || nombre.includes(texto) || email.includes(texto);
            const coincideRol    = !rol    || filRol === rol;
            const coincideEstado = !estado || filEst === estado;

            fila.style.display = (coincideTexto && coincideRol && coincideEstado) ? '' : 'none';
        });
    }

    busqueda.addEventListener('input', filtrarUsuarios);
    filtroRol.addEventListener('change', filtrarUsuarios);
    filtroEstado.addEventListener('change', filtrarUsuarios);
});