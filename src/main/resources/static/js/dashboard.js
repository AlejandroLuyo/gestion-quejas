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

            const banner = document.getElementById('sp-banner-ia');
            const esRevisionIA = c.estado === 'resolved' && c.agente === 'CSMate';

            if (esRevisionIA) {
                const csatTexto = c.csatPuntuacion
                    ? 'Calificación del cliente: ' + '★'.repeat(c.csatPuntuacion) + '☆'.repeat(5 - c.csatPuntuacion) + ' (' + c.csatPuntuacion + '/5)'
                    : 'Encuesta enviada, esperando respuesta del cliente.';
                document.getElementById('sp-csat-info').textContent = csatTexto;
                banner.style.display = 'block';
            } else {
                banner.style.display = 'none';
            }

            document.getElementById('slide-reply').style.display = esRevisionIA ? 'none' : '';
            document.getElementById('link-encuesta').style.display = 'none';

            document.getElementById('slide-panel').classList.add('open');
            document.getElementById('overlay').classList.add('show');
            cargarMensajes(id);
        });
}

function reabrirYDerivar() {
    if (!conversacionActualId) return;
    if (!confirm('¿Seguro que quieres reabrir esta conversación y derivarla a un agente?')) return;

    fetch('/quejas/' + conversacionActualId + '/reabrir-derivar', {
        method: 'POST'
    })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                document.getElementById('sp-estado').textContent = 'open';
                document.getElementById('sp-agente').textContent = data.agente;
                document.getElementById('sp-banner-ia').style.display = 'none';
            }
        })
        .catch(err => console.error('Error al reabrir y derivar:', err));
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
                if (m.remitente === 'NOTA_INTERNA') {
                    const div = document.createElement('div');
                    div.className = 'nota-interna';
                    div.innerHTML = `<i class="ti ti-note" style="font-size:14px; margin-top:1px;"></i><span>${m.contenido}</span>`;
                    lista.appendChild(div);
                    return;
                }
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

function mostrarFormularioTransferencia() {
    document.getElementById('slide-reply-actions').style.display = 'none';
    document.getElementById('form-transferencia').style.display = 'block';

    const btnConfirmar = document.getElementById('btn-confirmar-transferencia');
    fetch('/quejas/agentes-activos')
        .then(res => res.json())
        .then(agentes => {
            const select = document.getElementById('transferencia-select');
            select.innerHTML = '';
            if (agentes.length === 0) {
                select.innerHTML = '<option value="">No hay otros agentes disponibles</option>';
                btnConfirmar.disabled = true;
                return;
            }
            btnConfirmar.disabled = false;
            agentes.forEach(a => {
                const option = document.createElement('option');
                option.value = a.nombre;
                option.textContent = a.nombre;
                select.appendChild(option);
            });
        })
        .catch(err => console.error('Error cargando agentes:', err));
}

function cancelarTransferencia() {
    document.getElementById('form-transferencia').style.display = 'none';
    document.getElementById('slide-reply-actions').style.display = 'flex';
    document.getElementById('transferencia-nota').value = '';
}

function confirmarTransferencia() {
    if (!conversacionActualId) return;
    const agenteDestino = document.getElementById('transferencia-select').value;
    if (!agenteDestino) return;
    const nota = document.getElementById('transferencia-nota').value.trim();

    fetch('/quejas/' + conversacionActualId + '/transferir', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'agenteDestino=' + encodeURIComponent(agenteDestino) + '&nota=' + encodeURIComponent(nota)
    })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                document.getElementById('sp-estado').textContent = 'pending';
                document.getElementById('sp-agente').textContent = data.agente;
                cancelarTransferencia();
                cargarMensajes(conversacionActualId);

                const rows = document.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    if (row.getAttribute('onclick').includes(conversacionActualId)) {
                        const badge = row.querySelector('.status-badge');
                        if (badge) {
                            badge.textContent = 'pending';
                            badge.className = 'status-badge status-PENDIENTE';
                        }
                        const celdas = row.querySelectorAll('td');
                        if (celdas[4]) celdas[4].textContent = data.agente;
                    }
                });
            }
        })
        .catch(err => console.error('Error al transferir:', err));
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
                if (data.agente) {
                    document.getElementById('sp-agente').textContent = data.agente;
                }
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
                        if (data.agente) {
                            const celdas = row.querySelectorAll('td');
                            if (celdas[4]) celdas[4].textContent = data.agente;
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

function resolverYEnviarEncuesta() {
    if (!conversacionActualId) return;
    cambiarEstado('resolved');
    fetch('/csat/generar/' + conversacionActualId)
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                const baseUrl = window.location.origin;
                const linkCompleto = baseUrl + data.link;
                document.getElementById('encuesta-url').value = linkCompleto;
                document.getElementById('link-encuesta').style.display = 'block';
            }
        })
        .catch(err => console.error('Error generando encuesta:', err));
}

function copiarLink() {
    const input = document.getElementById('encuesta-url');
    input.select();
    navigator.clipboard.writeText(input.value).then(() => {
        const btn = input.nextElementSibling;
        btn.innerHTML = '<i class="ti ti-check"></i>';
        setTimeout(() => btn.innerHTML = '<i class="ti ti-copy"></i>', 2000);
    });
}

// === Chat CSMate (Portal Cliente) ===
function cargarMensajesPortal(id) {
    const lista = document.getElementById('portal-mensajes-list');
    fetch('/quejas/' + id + '/mensajes')
        .then(res => res.json())
        .then(mensajes => {
            lista.innerHTML = '';
            mensajes.forEach(m => {
                const esBot = m.remitente === 'BOT';
                const div = document.createElement('div');
                div.className = esBot ? 'portal-bubble portal-bubble-bot' : 'portal-bubble portal-bubble-cliente';
                div.textContent = m.contenido;
                lista.appendChild(div);
            });
            lista.scrollTop = lista.scrollHeight;
        })
        .catch(err => console.error('Error cargando mensajes del portal:', err));
}

let portalEnviando = false;

function enviarMensajePortal() {
    if (portalEnviando) return;

    const chatDiv = document.getElementById('portal-chat');
    const conversacionId = chatDiv.getAttribute('data-id');
    const input = document.getElementById('portal-reply-input');
    const mensaje = input.value.trim();
    if (!mensaje) return;

    portalEnviando = true;
    input.disabled = true;
    const sendBtn = document.querySelector('#portal-reply-row .portal-chat-send');
    const noMasBtn = document.getElementById('portal-btn-no-mas');
    if (sendBtn) sendBtn.disabled = true;
    if (noMasBtn) noMasBtn.disabled = true;

    mostrarEscribiendoPortal();

    fetch('/admin/portal-cliente/' + conversacionId + '/mensaje', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'mensaje=' + encodeURIComponent(mensaje)
    })
        .then(res => res.json())
        .then(data => {
            ocultarEscribiendoPortal();
            if (data.status !== 'ok') return;
            input.value = '';
            cargarMensajesPortal(conversacionId);

            if (data.estadoConversacion === 'CERRAR_SATISFECHO') {
                document.getElementById('portal-reply-row').style.display = 'none';
                document.getElementById('portal-btn-no-mas').style.display = 'none';
                const baseUrl = window.location.origin;
                document.getElementById('portal-encuesta-url').value = baseUrl + data.link;
                document.getElementById('portal-link-encuesta').style.display = 'block';
            } else if (data.estadoConversacion === 'ESCALAR') {
                document.getElementById('portal-reply-row').style.display = 'none';
                document.getElementById('portal-btn-no-mas').style.display = 'none';
                document.getElementById('portal-escalado-msg').style.display = 'block';
            }
        })
        .catch(err => {
            ocultarEscribiendoPortal();
            console.error('Error enviando mensaje del portal:', err);
        })
        .finally(() => {
            portalEnviando = false;
            input.disabled = false;
            if (sendBtn) sendBtn.disabled = false;
            if (noMasBtn) noMasBtn.disabled = false;
        });
}

function mostrarEscribiendoPortal() {
    const lista = document.getElementById('portal-mensajes-list');
    const typing = document.createElement('div');
    typing.id = 'portal-typing';
    typing.className = 'portal-bubble portal-bubble-bot portal-typing';
    typing.innerHTML = '<span></span><span></span><span></span>';
    lista.appendChild(typing);
    lista.scrollTop = lista.scrollHeight;
}

function ocultarEscribiendoPortal() {
    const typing = document.getElementById('portal-typing');
    if (typing) typing.remove();
}

function cerrarSinMasPreguntas() {
    document.getElementById('portal-reply-input').value = 'No tengo más preguntas, gracias';
    enviarMensajePortal();
}

function copiarLinkPortal() {
    const input = document.getElementById('portal-encuesta-url');
    input.select();
    navigator.clipboard.writeText(input.value).then(() => {
        const btn = input.nextElementSibling;
        btn.innerHTML = '<i class="ti ti-check"></i>';
        setTimeout(() => btn.innerHTML = '<i class="ti ti-copy"></i>', 2000);
    });
}

const portalChatDiv = document.getElementById('portal-chat');
if (portalChatDiv) {
    cargarMensajesPortal(portalChatDiv.getAttribute('data-id'));
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