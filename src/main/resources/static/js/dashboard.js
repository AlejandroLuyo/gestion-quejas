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

function enviarRespuesta() {
    if (!conversacionActualId) return;
    const input = document.getElementById('reply-input');
    const contenido = input.value.trim();
    if (!contenido) return;

    fetch('/quejas/' + conversacionActualId + '/responder', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'contenido=' + encodeURIComponent(contenido)
    })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                input.value = '';
                cargarMensajes(conversacionActualId);
            }
        })
        .catch(err => console.error('Error enviando respuesta:', err));
}

if (localStorage.getItem('darkMode') === 'true') {
    document.body.classList.add('dark');
    const icon = document.getElementById('dark-icon');
    if (icon) icon.className = 'ti ti-sun';
}

// FAB arrastrable
const fab = document.querySelector('.fab');
let isDragging = false;
let startX, startY, startLeft, startBottom;

fab.addEventListener('mousedown', (e) => {
    isDragging = false;
    startX = e.clientX;
    startY = e.clientY;
    startLeft = fab.getBoundingClientRect().left;
    startBottom = window.innerHeight - fab.getBoundingClientRect().bottom;

    const onMouseMove = (e) => {
        const dx = Math.abs(e.clientX - startX);
        const dy = Math.abs(e.clientY - startY);
        if (dx > 5 || dy > 5) isDragging = true;

        const newLeft = startLeft + (e.clientX - startX);
        const newBottom = startBottom - (e.clientY - startY);

        const newFabLeft = Math.max(0, Math.min(window.innerWidth - 60, newLeft));
        const newFabBottom = Math.max(0, Math.min(window.innerHeight - 60, newBottom));
        fab.style.left = newFabLeft + 'px';
        fab.style.bottom = newFabBottom + 'px';
        fab.style.right = 'auto';
        const chatWindow = document.getElementById('chat-window');
        chatWindow.style.left = newFabLeft + 'px';
        chatWindow.style.bottom = (newFabBottom + 60) + 'px';
        chatWindow.style.right = 'auto';
    };

    const onMouseUp = () => {
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
});

fab.addEventListener('click', (e) => {
    if (isDragging) {
        e.stopPropagation();
        return;
    }
    toggleChat();
});