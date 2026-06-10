document.querySelector('form').addEventListener('submit', function() {
    const btn = document.querySelector('.btn-login');
    btn.textContent = 'Iniciando sesión...';
    btn.disabled = true;
});