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

    // Icono del menú lateral
    const sidebarIcon = document.getElementById('dark-icon');
    if (sidebarIcon) {
        sidebarIcon.className = isDark ? 'ti ti-sun' : 'ti ti-moon';
    }

    // Botón superior
    const topIcon = document.getElementById('dark-icon-btn');
    const topText = document.getElementById('dark-text-btn');

    if (topIcon) {
        topIcon.className = isDark ? 'ti ti-sun' : 'ti ti-moon';
    }

    if (topText) {
        topText.textContent = isDark ? 'Modo claro' : 'Modo oscuro';
    }

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
            // Mostrar u ocultar el botón de reembolso según el contact reason
            const btnReembolsoWrap = document.getElementById('btn-reembolso-wrap');
            if (btnReembolsoWrap) {
                btnReembolsoWrap.style.display = c.contactReason === 'refund_request' ? 'block' : 'none';
            }
            // Cerrar panel de reembolso si quedó abierto de la conversación anterior
            cerrarPanelReembolso();
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
    cerrarPanelReembolso();
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

document.addEventListener("DOMContentLoaded", () => {

    const isDark = localStorage.getItem("darkMode") === "true";

    if (isDark) {
        document.body.classList.add("dark");
    }

    // Icono del menú lateral
    const sidebarIcon = document.getElementById("dark-icon");
    if (sidebarIcon) {
        sidebarIcon.className = isDark ? "ti ti-sun" : "ti ti-moon";
    }

    // Botón superior
    const topIcon = document.getElementById("dark-icon-btn");
    const topText = document.getElementById("dark-text-btn");

    if (topIcon) {
        topIcon.className = isDark ? "ti ti-sun" : "ti ti-moon";
    }

    if (topText) {
        topText.textContent = isDark ? "Modo claro" : "Modo oscuro";
    }

});

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
            lista.scrollTop = lista.scrollHeight; // auto-scroll al último mensaje
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
            if (noMasBtn && noMasBtn.style.display !== 'none') noMasBtn.disabled = false;
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
    const btn = document.getElementById('portal-btn-no-mas');
    if (btn) btn.disabled = true;
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
// === Panel de reembolso ===
let reembolsoData = {};

function abrirPanelReembolso() {
    if (!conversacionActualId) return;
    fetch('/reembolso/' + conversacionActualId)
        .then(res => res.json())
        .then(data => {
            if (data.status !== 'ok') return;
            reembolsoData = data;
            renderPanelReembolso(data);
            document.getElementById('reembolso-panel').style.display = 'flex';
            setTimeout(() => document.getElementById('reembolso-panel').classList.add('open'), 10);
        })
        .catch(err => console.error('Error cargando reembolso:', err));
}

function cerrarPanelReembolso() {
    const panel = document.getElementById('reembolso-panel');
    panel.classList.remove('open');
    setTimeout(() => panel.style.display = 'none', 200);
}

function renderPanelReembolso(data) {
    const body = document.getElementById('reembolso-panel-body');
    const precio = data.precio || 0;
    const bloqueado = data.orderStatus === 'completed';
    const estado = data.botRefundStatus;

    // Caso: visa ya entregada — formulario bloqueado
    if (bloqueado) {
        body.innerHTML = `
            <div class="reembolso-alerta">
                <i class="ti ti-alert-circle"></i>
                <div>
                    <strong>Reembolso no procede</strong><br>
                    La visa ya fue entregada (estado: completed). Según política, no se puede procesar un reembolso.
                </div>
            </div>`;
        return;
    }

    // Caso: ya tiene resultado final
    // Caso: rechazado por supervisor — el agente puede reintentar o cerrar
    if (estado === 'rechazado_supervisor') {
        body.innerHTML = `
        <div class="reembolso-alerta" style="background:#fef3c7; border-color:#fde68a; color:#92400e;">
            <i class="ti ti-alert-circle" style="color:#d97706;"></i>
            <div>
                <strong>Solicitud rechazada por el supervisor</strong><br>
                ${data.supervisorNotes ? data.supervisorNotes : 'Sin nota adicional.'}
            </div>
        </div>
        <div class="reembolso-field">
            <label>Motivo que justifica el reembolso</label>
            <select id="ref-motivo">
                <option value="amenaza_legal" ${data.refundReasonCategory === 'amenaza_legal' ? 'selected' : ''}>Amenaza de acción legal</option>
                <option value="redes_sociales" ${data.refundReasonCategory === 'redes_sociales' ? 'selected' : ''}>Publicación en redes sociales</option>
                <option value="error_empresa" ${data.refundReasonCategory === 'error_empresa' ? 'selected' : ''}>Error comprobado de la empresa</option>
            </select>
        </div>
        <div class="reembolso-grid">
            <div class="reembolso-field">
                <label>Sugerido (S/)</label>
                <input type="text" value="${precio.toFixed(2)}" readonly/>
            </div>
            <div class="reembolso-field">
                <label>Acordado (S/)</label>
                <input type="number" id="ref-monto" value="${data.refundAmount?.toFixed(2)}"
                       step="0.01" oninput="actualizarPorcentaje(${precio})"/>
            </div>
        </div>
        <div class="reembolso-field">
            <label>Porcentaje</label>
            <input type="text" id="ref-porcentaje" value="${data.refundPercent}%" readonly/>
        </div>
        <div class="reembolso-field">
            <label>Notas para el supervisor</label>
            <textarea id="ref-notas" rows="2" placeholder="Explica los cambios realizados...">${data.agentNotes || ''}</textarea>
        </div>
        <div class="reembolso-acciones">
            <button class="btn-reembolso-aprobar" onclick="enviarASupervisor(${precio})">
                Reintentar solicitud
            </button>
            <button class="btn-reembolso-denegar" onclick="cerrarDefinitivamente()">
                Cerrar definitivamente
            </button>
        </div>`;
        return;
    }

// Caso: cerrado definitivamente
    if (estado === 'cerrado') {
        const textos = {
            'aprobado': '✅ Reembolso aprobado y enviado a finanzas.',
            'denegado': '❌ Reembolso denegado definitivamente.',
        };
        body.innerHTML = `<div class="reembolso-resultado">${textos[data.refundResult] || 'Caso cerrado.'}</div>`;
        return;
    }

    // Caso: pendiente de aprobación del supervisor
    if (estado === 'pendiente_supervisor') {
        const esSupervisor = document.querySelector('.sidebar-agent-role')?.textContent?.includes('Supervisor')
            || document.querySelector('.sidebar-agent-role')?.textContent?.includes('Administrador');

        body.innerHTML = `
            <div class="reembolso-info-box">
                <strong style="font-size:13px; margin-bottom:4px;">Pendiente de aprobación</strong>
                <div class="reembolso-info-row"><span>Motivo</span><span>${traducirMotivo(data.refundReasonCategory)}</span></div>
                <div class="reembolso-info-row"><span>Monto acordado</span><span>S/ ${data.refundAmount?.toFixed(2)}</span></div>
                <div class="reembolso-info-row"><span>Porcentaje</span><span>${data.refundPercent}%</span></div>
                ${data.agentNotes ? `<div class="reembolso-info-row"><span>Notas agente</span><span>${data.agentNotes}</span></div>` : ''}
            </div>
            ${esSupervisor ? `
            <div class="reembolso-field">
                <label>Monto final a devolver (S/)</label>
                <input type="number" id="sup-monto" value="${data.refundAmount?.toFixed(2)}" step="0.01"/>
            </div>
            <div class="reembolso-field">
                <label>Notas para finanzas</label>
                <textarea id="sup-notas" rows="3" placeholder="Instrucciones para el área de finanzas..."></textarea>
            </div>
            <div class="reembolso-acciones">
                <button class="btn-reembolso-aprobar" onclick="aprobarReembolso()">Aprobar y enviar a finanzas</button>
                <button class="btn-reembolso-denegar" onclick="rechazarReembolso()">Rechazar solicitud</button>
            </div>` : `<p style="font-size:12px; color:var(--color-text-tertiary); text-align:center;">En espera de aprobación del supervisor.</p>`}
        `;
        return;
    }

    // Caso: formulario inicial para el agente
    body.innerHTML = `
        <div class="reembolso-field">
            <label>Motivo que justifica el reembolso</label>
            <select id="ref-motivo">
                <option value="amenaza_legal">Amenaza de acción legal</option>
                <option value="redes_sociales">Publicación en redes sociales</option>
                <option value="error_empresa">Error comprobado de la empresa</option>
            </select>
        </div>
        <div class="reembolso-grid">
            <div class="reembolso-field">
                <label>Monto sugerido (S/)</label>
                <input type="text" id="ref-sugerido" value="${precio.toFixed(2)}" readonly/>
            </div>
            <div class="reembolso-field">
                <label>Monto acordado (S/)</label>
                <input type="number" id="ref-monto" value="${precio.toFixed(2)}" step="0.01"
                       oninput="actualizarPorcentaje(${precio})"/>
            </div>
        </div>
        <div class="reembolso-field">
            <label>Porcentaje del precio original</label>
            <input type="text" id="ref-porcentaje" value="100%" readonly/>
        </div>
        <div class="reembolso-field">
            <label>Notas para el supervisor</label>
            <textarea id="ref-notas" rows="3" placeholder="Contexto adicional para la aprobación..."></textarea>
        </div>
        <div class="reembolso-acciones">
            <button class="btn-reembolso-aprobar" onclick="enviarASupervisor(${precio})">Enviar a supervisor</button>
            <button class="btn-reembolso-denegar" onclick="denegarReembolso()">Denegar reembolso</button>
        </div>
    `;
}

function traducirMotivo(key) {
    const map = {
        'amenaza_legal': 'Amenaza de acción legal',
        'redes_sociales': 'Publicación en redes sociales',
        'error_empresa': 'Error comprobado de la empresa'
    };
    return map[key] || key;
}

function actualizarPorcentaje(precio) {
    const monto = parseFloat(document.getElementById('ref-monto').value) || 0;
    const pct = precio > 0 ? ((monto / precio) * 100).toFixed(1) : 0;
    document.getElementById('ref-porcentaje').value = pct + '%';
}

function enviarASupervisor(precio) {
    const motivo = document.getElementById('ref-motivo').value;
    const monto = parseFloat(document.getElementById('ref-monto').value);
    const notas = document.getElementById('ref-notas').value;

    if (!monto || monto <= 0) { alert('Ingresa un monto válido.'); return; }

    fetch('/reembolso/' + conversacionActualId + '/enviar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `reasonCategory=${encodeURIComponent(motivo)}&amount=${monto}&precio=${precio}&agentNotes=${encodeURIComponent(notas)}`
    })
        .then(res => res.json())
        .then(data => { if (data.status === 'ok') abrirPanelReembolso(); })
        .catch(err => console.error(err));
}

function denegarReembolso() {
    if (!confirm('¿Confirmas que el reembolso será denegado?')) return;
    fetch('/reembolso/' + conversacionActualId + '/denegar', { method: 'POST' })
        .then(res => res.json())
        .then(data => { if (data.status === 'ok') abrirPanelReembolso(); })
        .catch(err => console.error(err));
}

function aprobarReembolso() {
    const montoFinal = parseFloat(document.getElementById('sup-monto').value);
    const notas = document.getElementById('sup-notas').value;
    const precio = reembolsoData.precio || 0;

    if (!montoFinal || montoFinal <= 0) { alert('Ingresa un monto final válido.'); return; }
    if (!confirm('¿Confirmas la aprobación y envío a finanzas?')) return;

    fetch('/reembolso/' + conversacionActualId + '/aprobar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `montoFinal=${montoFinal}&precio=${precio}&supervisorNotes=${encodeURIComponent(notas)}`
    })
        .then(res => res.json())
        .then(data => { if (data.status === 'ok') abrirPanelReembolso(); })
        .catch(err => console.error(err));
}

function rechazarReembolso() {
    const notas = document.getElementById('sup-notas')?.value || '';
    if (!confirm('¿Confirmas el rechazo de esta solicitud?')) return;

    fetch('/reembolso/' + conversacionActualId + '/rechazar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `supervisorNotes=${encodeURIComponent(notas)}`
    })
        .then(res => res.json())
        .then(data => { if (data.status === 'ok') abrirPanelReembolso(); })
        .catch(err => console.error(err));
}

function cerrarDefinitivamente() {
    if (!conversacionActualId) return;
    if (!confirm('¿Confirmas que el reembolso será denegado definitivamente?')) return;

    fetch('/reembolso/' + conversacionActualId + '/cerrar-definitivo', {
        method: 'POST'
    })
        .then(res => res.json())
        .then(data => { if (data.status === 'ok') abrirPanelReembolso(); })
        .catch(err => console.error(err));
}


// FAB arrastrable
const chatContainer = document.querySelector('.chat-container');
const fab = document.getElementById('fab');

if (chatContainer && fab) {
    let isDragging = false;
    let startX, startY, startRight, startBottom;

    fab.addEventListener('mousedown', (e) => {
        isDragging = false;

        startX = e.clientX;
        startY = e.clientY;

        const rect = chatContainer.getBoundingClientRect();
        startRight = window.innerWidth - rect.right;
        startBottom = window.innerHeight - rect.bottom;

        const onMouseMove = (e) => {
            const dx = e.clientX - startX;
            const dy = e.clientY - startY;

            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                isDragging = true;
            }

            let newRight = startRight - dx;
            let newBottom = startBottom - dy;

            newRight = Math.max(12, Math.min(window.innerWidth - 380, newRight));
            newBottom = Math.max(12, Math.min(window.innerHeight - 80, newBottom));

            chatContainer.style.right = newRight + 'px';
            chatContainer.style.bottom = newBottom + 'px';
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
}