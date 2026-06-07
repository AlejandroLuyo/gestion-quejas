let conversacionActualId = null;

function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const wrapper = document.getElementById('main-wrapper');
    sidebar.classList.toggle('open');
    wrapper.classList.toggle('sidebar-open');
}

function toggleChat() {
    document.getElementById('chat-window').classList.toggle('closed');
}

function toggleDark() {
    document.body.classList.toggle('dark');
    const isDark = document.body.classList.contains('dark');
    const icon = document.getElementById('dark-icon');
    icon.className = isDark ? 'ti ti-sun' : 'ti ti-moon';
    localStorage.setItem('darkMode', isDark);
}

function openPanel(id) {
    conversacionActualId = id;
    fetch('/quejas/' + id + '/json')
        .then(res => res.json())
        .then(c => {
            document.getElementById('sp-name').textContent = c.orderId || '-';
            document.getElementById('sp-sub').textContent = c.contactReason || '-';
            document.getElementById('sp-reason').textContent = c.contactReason || '-';
            document.getElementById('sp-estado').textContent = c.estado || '-';
            document.getElementById('sp-origen').textContent = c.canal || '-';
            document.getElementById('sp-fecha').textContent = c.orderId || '-';
            document.getElementById('sp-agente').textContent = c.agente || 'Sin asignar';
            document.getElementById('sp-fechaCreacion').textContent = c.fechaCreacion || '-';
            document.getElementById('slide-panel').classList.add('open');
            document.getElementById('overlay').classList.add('show');
            cargarMensajes(id);
        });
}

function cargarMensajes(id) {
    const lista = document.getElementById('mensajes-list');
    lista.innerHTML = '<div style="text-align:center; color:#94a3b8; font-size:12px; padding:20px;">Cargando...</div>';
    fetch('/quejas/' + id + '/mensajes')
        .then(res => {
            console.log('Status mensajes:', res.status);
            return res.json();
        })
        .then(mensajes => {
            console.log('Mensajes recibidos:', mensajes);
            if (mensajes.length === 0) {
                lista.innerHTML = '<div style="text-align:center; color:#94a3b8; font-size:12px; padding:20px;">Sin mensajes aún.</div>';
                return;
            }
            lista.innerHTML = '';
            mensajes.forEach(m => {
                const esAgente = m.remitente === 'AGENTE';
                const div = document.createElement('div');
                div.className = 'msg-wrap' + (esAgente ? ' right' : '');
                div.innerHTML = `
                    <div class="bubble ${esAgente ? 'bubble-out' : 'bubble-in'}">${m.contenido}</div>
                    <div class="msg-time">${m.remitente} · ${m.fechaEnvio}</div>
                `;
                lista.appendChild(div);
            });
        })
        .catch(err => console.error('Error cargando mensajes:', err));
}

function closePanel() {
    document.getElementById('slide-panel').classList.remove('open');
    document.getElementById('overlay').classList.remove('show');
    conversacionActualId = null;
}

function cambiarEstado(nuevoEstado) {
    if (!conversacionActualId) return;

    fetch('/quejas/' + conversacionActualId + '/estado', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'estado=' + nuevoEstado
    })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                document.getElementById('sp-estado').textContent = nuevoEstado;
                // Actualizar el badge en la tabla
                const rows = document.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    if (row.getAttribute('onclick').includes(conversacionActualId)) {
                        const badge = row.querySelector('.status-badge');
                        if (badge) {
                            badge.textContent = nuevoEstado;
                            badge.className = 'status-badge';
                            if (nuevoEstado === 'open') badge.classList.add('status-EN_PROCESO');
                            else if (nuevoEstado === 'resolved') badge.classList.add('status-RESUELTO');
                            else badge.classList.add('status-PENDIENTE');
                        }
                    }
                });
            }
        });
}

function sendMsg() {
    const input = document.getElementById('chat-input');
    const text = input.value.trim();
    if (!text) return;
    const msgs = document.getElementById('chat-messages');
    msgs.innerHTML += `<div class="msg msg-user">${text}</div>`;
    input.value = '';
    msgs.scrollTop = msgs.scrollHeight;
}

if (localStorage.getItem('darkMode') === 'true') {
    document.body.classList.add('dark');
    const icon = document.getElementById('dark-icon');
    if (icon) icon.className = 'ti ti-sun';
}