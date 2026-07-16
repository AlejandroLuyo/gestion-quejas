function guardarConfiguracion() {
    const email           = document.getElementById('cfgEmail').value.trim();
    const password        = document.getElementById('cfgPassword').value;
    const passwordConfirm = document.getElementById('cfgPasswordConfirm').value;
    const idioma          = document.getElementById('cfgIdioma').value;
    const zonaHoraria     = document.getElementById('cfgZona').value;
    const errorDiv        = document.getElementById('cfgError');
    const exitoDiv        = document.getElementById('cfgExito');
    const btnGuardar      = document.getElementById('btnGuardarConfig');
    const firma = document.getElementById('cfgFirma').value.trim();

    errorDiv.style.display = 'none';
    exitoDiv.style.display = 'none';

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
    if (password && password !== passwordConfirm) {
        errorDiv.textContent = 'Las contraseñas no coinciden.';
        errorDiv.style.display = 'block';
        return;
    }

    if (!confirm('¿Estás seguro de guardar los cambios?')) return;

    btnGuardar.disabled = true;
    btnGuardar.textContent = 'Guardando...';

    const params = new URLSearchParams();
    params.append('email', email);
    params.append('idioma', idioma);
    params.append('zonaHoraria', zonaHoraria);
    params.append('firma', firma);
    if (password) params.append('password', password);

    // Recolecta el estado de los feature flags (solo existen en el DOM si el usuario es ADMINISTRADOR)
    const flagInputs = document.querySelectorAll('.flag-toggle-input');
    const estadosFlags = {};
    flagInputs.forEach(input => {
        estadosFlags[input.dataset.flagKey] = input.checked;
    });

    fetch('/configuracion/guardar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString()
    })
        .then(res => {
            if (!res.ok) {
                throw new Error('Error al guardar el perfil');
            }
            // Si hay flags en la página, los guardamos en un segundo paso
            if (flagInputs.length > 0) {
                return fetch('/config/features/guardar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(estadosFlags)
                }).then(res2 => {
                    if (!res2.ok) {
                        throw new Error('Error al guardar las funcionalidades');
                    }
                });
            }
        })
        .then(() => {
            exitoDiv.textContent = 'Cambios guardados correctamente.';
            exitoDiv.style.display = 'block';
            document.getElementById('cfgPassword').value = '';
            document.getElementById('cfgPasswordConfirm').value = '';
        })
        .catch(() => {
            errorDiv.textContent = 'Ocurrió un error al guardar. Intenta nuevamente.';
            errorDiv.style.display = 'block';
        })
        .finally(() => {
            btnGuardar.disabled = false;
            btnGuardar.innerHTML = '<i class="ti ti-device-floppy"></i> Guardar cambios';
        });
}