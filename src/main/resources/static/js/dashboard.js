let conversacionActualId = null;
let chatIniciado = false;
let emReembolsoContactReason = null;
const usuarioActualEsSupervisorOAdmin =
    !!document.getElementById('rol-admin-flag') || !!document.getElementById('rol-supervisor-flag');

function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const wrapper = document.getElementById('main-wrapper');

    sidebar.classList.toggle('open');
    wrapper.classList.toggle('sidebar-open');
}

function toggleChat() {
    const chatWindow = document.getElementById('chat-window');
    chatWindow.classList.toggle('closed');

    if (!chatWindow.classList.contains('closed') && !chatIniciado) {
        chatIniciado = true;
        cargarSaludoAsistente();
    }
}

function openPanelSegunCanal(id, canal) {
    if (canal === 'email') {
        openEmailPanel(id);
    } else {
        openPanel(id);
    }
}

function cargarSaludoAsistente() {
    fetch('/api/asistente/saludo')
        .then(res => res.json())
        .then(data => {
            const msgs = document.getElementById('chat-messages');
            msgs.innerHTML = `<div class="msg msg-bot">${data.saludo}</div>`;
        })
        .catch(err => console.error('Error cargando saludo del asistente:', err));
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

function traducirContactReason(valor) {
    const mapa = {
        'payment_issues':          'Problemas de pago',
        'refund_request':          'Solicitud de reembolso',
        'status_information':      'Información de estado',
        'cx_modify':               'Modificación de orden',
        'deliverable_information': 'Información de entrega',
        'requirements_assistance': 'Asistencia de requisitos',
        'upload_support':          'Soporte de carga',
        'consulta_general':        'Consulta general'
    };
    return mapa[valor] || valor || '-';
}

function traducirEstado(valor) {
    const mapa = {
        'open':     'En proceso',
        'pending':  'Pendiente',
        'resolved': 'Resuelto'
    };
    return mapa[valor] || valor || '-';
}

function calcularBloqueosPorEstado(estado) {
    return {
        abrir: estado === 'open',
        reasignar: estado === 'resolved',
        resolver: estado === 'resolved'
    };
}

function openPanel(id) {
    conversacionActualId = id;
    fetch('/quejas/' + id + '/json')
        .then(res => res.json())
        .then(c => {
            document.getElementById('sp-name').textContent = c.orderId || '-';
            document.getElementById('sp-sub').textContent = traducirContactReason(c.contactReason);
            document.getElementById('sp-reason').textContent = traducirContactReason(c.contactReason);
            document.getElementById('sp-estado').textContent = traducirEstado(c.estado);
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

            aplicarPermisosPanel(c.agente, c.estado);

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
                document.getElementById('sp-estado').textContent = traducirEstado('open');
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
                document.getElementById('sp-estado').textContent = traducirEstado('pending');
                document.getElementById('sp-agente').textContent = data.agente;
                cancelarTransferencia();
                cargarMensajes(conversacionActualId);

                const rows = document.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    if (row.getAttribute('onclick').includes(conversacionActualId)) {
                        const badge = row.querySelector('.status-badge');
                        if (badge) {
                            badge.textContent = traducirEstado('pending');
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

function openEmailPanel(id) {
    conversacionActualId = id;

    fetch('/quejas/' + id + '/json')
        .then(res => res.json())
        .then(c => {
            document.getElementById('em-orderid').textContent = c.orderId !== '-' ? c.orderId : (c.remitenteEmail || '-');
            document.getElementById('em-reason').textContent = traducirContactReason(c.contactReason);

            emReembolsoContactReason = c.contactReason;
            document.getElementById('em-reclasificar-aviso').style.display = 'none';
            document.getElementById('em-form-reembolso').style.display = 'none';
            document.getElementById('em-form-reembolso').innerHTML = formularioReembolsoHTML();
            cargarEstadoReembolso(id);
            emReembolsoContactReason = c.contactReason;
            document.getElementById('em-reembolso-wrap').style.display = 'block';
            document.getElementById('em-btn-reembolso').style.display = 'block';
            document.getElementById('em-reclasificar-aviso').style.display = 'none';
            document.getElementById('em-form-reembolso').style.display = 'none';

            const badge = document.getElementById('em-estado-badge');
            const estadoTexto = c.estado === 'open' ? 'En proceso' : c.estado === 'pending' ? 'Pendiente' : c.estado === 'resolved' ? 'Resuelto' : '-';
            badge.textContent = estadoTexto;
            badge.className = 'status-badge ' + (c.estado === 'open' ? 'status-EN_PROCESO' : c.estado === 'resolved' ? 'status-RESUELTO' : 'status-PENDIENTE');

            document.getElementById('em-de').textContent = c.remitenteEmail || '-';
            document.getElementById('em-fecha').textContent = c.fechaCreacion || '-';
            document.getElementById('em-asunto').textContent = c.asunto || '-';
            document.getElementById('em-de').textContent = c.remitenteEmail || '-';
            document.getElementById('em-fecha').textContent = c.fechaCreacion || '-';
            document.getElementById('em-asunto').textContent = c.asunto || '-';

            aplicarPermisosEmail(c.agente, c.estado);

            cargarMensajesEmail(id);
        })
        .catch(err => console.error('Error cargando conversación de email:', err));

    document.getElementById('email-modal-overlay').style.display = 'block';
    document.getElementById('email-modal').style.display = 'flex';
}

function aplicarPermisosPanel(agenteAsignado, estado) {
    const tienePermiso = usuarioActualEsSupervisorOAdmin ||
        (agenteAsignado && agenteAsignado === usuarioActualNombre) ||
        agenteAsignado === 'CSMate' || !agenteAsignado;

    const bloqueos = calcularBloqueosPorEstado(estado);

    document.querySelector('.btn-estado-abrir').disabled = !tienePermiso || bloqueos.abrir;
    document.querySelector('.btn-estado-pendiente').disabled = !tienePermiso || bloqueos.reasignar;
    document.querySelector('.btn-estado-resolver').disabled = !tienePermiso || bloqueos.resolver;

    const replyInput = document.getElementById('reply-input');
    const replyBtn = document.querySelector('#slide-panel .reply-send');
    if (replyInput) replyInput.disabled = !tienePermiso;
    if (replyBtn) replyBtn.disabled = !tienePermiso;

    let aviso = document.getElementById('sp-sin-permiso-aviso');
    if (!tienePermiso) {
        if (!aviso) {
            aviso = document.createElement('div');
            aviso.id = 'sp-sin-permiso-aviso';
            aviso.className = 'reply-aviso reply-aviso-advertencia';
            aviso.textContent = 'Este caso está asignado a otro agente. Solo puedes observarlo.';
            document.getElementById('slide-reply').prepend(aviso);
        }
        aviso.style.display = 'block';
    } else if (aviso) {
        aviso.style.display = 'none';
    }
}

function mostrarFormularioTransferenciaEmail() {
    document.getElementById('em-reembolso-wrap').style.display = 'none';
    document.getElementById('em-link-encuesta').style.display = 'none';
    document.getElementById('em-transferencia-wrap').style.display = 'block';

    const btnConfirmar = document.getElementById('em-btn-confirmar-transferencia');
    fetch('/quejas/agentes-activos')
        .then(res => res.json())
        .then(agentes => {
            const select = document.getElementById('em-transferencia-select');
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

function cancelarTransferenciaEmail() {
    document.getElementById('em-transferencia-wrap').style.display = 'none';
    document.getElementById('em-transferencia-nota').value = '';
}

function confirmarTransferenciaEmail() {
    if (!conversacionActualId) return;
    const agenteDestino = document.getElementById('em-transferencia-select').value;
    if (!agenteDestino) return;
    const nota = document.getElementById('em-transferencia-nota').value.trim();

    fetch('/quejas/' + conversacionActualId + '/transferir', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'agenteDestino=' + encodeURIComponent(agenteDestino) + '&nota=' + encodeURIComponent(nota)
    })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                cancelarTransferenciaEmail();
                openEmailPanel(conversacionActualId);
            }
        })
        .catch(err => console.error('Error al transferir:', err));
}

function resolverYEnviarEncuestaEmail() {
    if (!conversacionActualId) return;

    document.getElementById('em-reembolso-wrap').style.display = 'none';
    document.getElementById('em-transferencia-wrap').style.display = 'none';

    fetch('/quejas/' + conversacionActualId + '/estado', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'estado=resolved'
    })
        .then(() => openEmailPanel(conversacionActualId))
        .then(() => fetch('/csat/generar/' + conversacionActualId))
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                const baseUrl = window.location.origin;
                const linkCompleto = baseUrl + data.link;
                document.getElementById('em-encuesta-url').value = linkCompleto;
                document.getElementById('em-link-encuesta').style.display = 'block';
            }
        })
        .catch(err => console.error('Error generando encuesta:', err));
}

function aplicarPermisosEmail(agenteAsignado, estado) {
    const tienePermiso = usuarioActualEsSupervisorOAdmin ||
        (agenteAsignado && agenteAsignado === usuarioActualNombre);

    const bloqueos = calcularBloqueosPorEstado(estado);

    document.querySelector('.email-action-abrir').disabled = !tienePermiso || bloqueos.abrir;
    document.querySelector('.email-action-reasignar').disabled = !tienePermiso || bloqueos.reasignar;
    document.querySelector('.email-action-resolver').disabled = !tienePermiso || bloqueos.resolver;

    const replyInput = document.getElementById('em-reply-input');
    const replyBtn = document.querySelector('.reply-send');
    if (replyInput) replyInput.disabled = !tienePermiso;
    if (replyBtn) replyBtn.disabled = !tienePermiso;

    let aviso = document.getElementById('em-sin-permiso-aviso');
    if (!tienePermiso) {
        if (!aviso) {
            aviso = document.createElement('div');
            aviso.id = 'em-sin-permiso-aviso';
            aviso.className = 'reply-aviso reply-aviso-advertencia';
            aviso.textContent = 'Este caso está asignado a otro agente. Solo puedes observarlo.';
            document.querySelector('.email-modal-footer').prepend(aviso);
        }
        aviso.style.display = 'block';
    } else if (aviso) {
        aviso.style.display = 'none';
    }
}

function copiarLinkEmail() {
    const input = document.getElementById('em-encuesta-url');
    input.select();
    navigator.clipboard.writeText(input.value).then(() => {
        const btn = input.nextElementSibling;
        btn.innerHTML = '<i class="ti ti-check"></i>';
        setTimeout(() => btn.innerHTML = '<i class="ti ti-copy"></i>', 2000);
    });
}

function cargarEstadoReembolso(id) {
    fetch('/reembolso/' + id)
        .then(res => res.json())
        .then(data => {
            const btn = document.getElementById('em-btn-reembolso');
            const wrap = document.getElementById('em-reembolso-wrap');

            if (!data.botRefundStatus) {
                btn.style.display = 'block';
                let extra = wrap.querySelector('.email-reembolso-estado');
                if (extra) extra.remove();
                return;
            }

            btn.style.display = 'none';
            let mensaje = '';
            if (data.botRefundStatus === 'pendiente_supervisor') {
                mensaje = 'Solicitud enviada al supervisor, pendiente de aprobación.';
            } else if (data.refundResult === 'aprobado') {
                mensaje = 'Reembolso aprobado por el supervisor.';
            } else if (data.refundResult === 'denegado') {
                mensaje = 'Reembolso denegado.';
            } else if (data.refundResult === 'rechazado_supervisor') {
                mensaje = 'Solicitud rechazada por el supervisor.';
            }

            let extra = wrap.querySelector('.email-reembolso-estado');
            if (!extra) {
                extra = document.createElement('p');
                extra.className = 'email-reembolso-estado';
                wrap.insertBefore(extra, document.getElementById('em-reclasificar-aviso'));
            }
            extra.textContent = mensaje;
        })
        .catch(err => console.error('Error consultando estado de reembolso:', err));
}

function formularioReembolsoHTML() {
    return `
        <label class="email-form-label">Categoría del motivo</label>
        <select id="em-reembolso-categoria" class="email-form-input">
            <option value="amenaza_legal">Amenaza de acción legal</option>
            <option value="redes_sociales">Publicación en redes sociales</option>
            <option value="error_empresa">Error comprobado de la empresa</option>
        </select>

        <label class="email-form-label">Monto (S/)</label>
        <input type="number" id="em-reembolso-monto" class="email-form-input" step="0.01" placeholder="0.00" />

        <label class="email-form-label">Notas (opcional)</label>
        <textarea id="em-reembolso-notas" class="email-form-input" rows="2" placeholder="Notas para el supervisor..."></textarea>

        <button id="em-btn-enviar-reembolso" onclick="enviarReembolsoInline()">Enviar a supervisor</button>
    `;
}

function closeEmailPanel() {
    document.getElementById('email-modal-overlay').style.display = 'none';
    document.getElementById('email-modal').style.display = 'none';
}

function cargarMensajesEmail(id) {
    const cont = document.getElementById('em-mensajes-list');
    cont.innerHTML = '<div style="text-align:center; color:#94a3b8; font-size:12px; padding:20px;">Cargando...</div>';

    fetch('/quejas/' + id + '/mensajes')
        .then(res => res.json())
        .then(mensajes => {
            if (mensajes.length === 0) {
                cont.innerHTML = '<div style="text-align:center; color:#94a3b8; font-size:12px; padding:20px;">Sin mensajes aún.</div>';
                return;
            }
            cont.innerHTML = '';
            mensajes.forEach(m => {
                if (m.remitente === 'NOTA_INTERNA') {
                    const div = document.createElement('div');
                    div.className = 'nota-interna';
                    div.innerHTML = `<i class="ti ti-note" style="font-size:14px; margin-top:1px;"></i><span>${m.contenido}</span>`;
                    cont.appendChild(div);
                    return;
                }
                const nombreRemitente = m.remitente === 'AGENTE' ? 'Agente' : m.remitente === 'BOT' ? 'CSMate' : 'Cliente';
                const div = document.createElement('div');
                div.className = 'email-msg email-msg-' + m.remitente.toLowerCase();
                div.innerHTML = `
                    <div class="email-msg-header">${nombreRemitente} &middot; ${m.fechaEnvio}</div>
                    <div class="email-msg-body">${m.contenido}</div>
                `;
                cont.appendChild(div);
            });
        })
        .catch(err => console.error('Error cargando mensajes:', err));
}

function cambiarEstadoEmail(estado) {
    fetch('/quejas/' + conversacionActualId + '/estado', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'estado=' + estado
    })
        .then(res => res.json())
        .then(() => openEmailPanel(conversacionActualId))
        .catch(err => console.error('Error cambiando estado:', err));
}

function enviarRespuestaEmail() {
    if (!conversacionActualId) return;
    const input = document.getElementById('em-reply-input');
    const contenido = input.value.trim();
    if (!contenido) return;

    fetch('/quejas/' + conversacionActualId + '/responder', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'contenido=' + encodeURIComponent(contenido)
    })
        .then(res => res.json())
        .then(data => {
            const aviso = document.getElementById('em-reply-aviso');
            if (data.status === 'ok') {
                input.value = '';
                cargarMensajesEmail(conversacionActualId);
                mostrarAvisoEnvioEn(aviso, 'Correo enviado correctamente.', 'exito');
            } else if (data.status === 'sin_destinatario') {
                input.value = '';
                cargarMensajesEmail(conversacionActualId);
                mostrarAvisoEnvioEn(aviso, data.mensaje || 'Sin destinatario. Verifica manualmente.', 'advertencia');
            } else {
                mostrarAvisoEnvioEn(aviso, 'No se pudo enviar la respuesta. Intenta de nuevo.', 'error');
            }
        })
        .catch(err => {
            console.error('Error enviando respuesta:', err);
            mostrarAvisoEnvioEn(document.getElementById('em-reply-aviso'), 'Error de conexión.', 'error');
        });
}

function clicProcesarReembolso() {
    if (emReembolsoContactReason === 'refund_request') {
        document.getElementById('em-btn-reembolso').style.display = 'none';
        document.getElementById('em-form-reembolso').style.display = 'block';
    } else {
        document.getElementById('em-btn-reembolso').style.display = 'none';
        document.getElementById('em-reclasificar-aviso').style.display = 'block';
    }
}

function cancelarReclasificacion() {
    document.getElementById('em-reclasificar-aviso').style.display = 'none';
    document.getElementById('em-btn-reembolso').style.display = 'block';
}

function confirmarReclasificacion() {
    fetch('/quejas/' + conversacionActualId + '/reclasificar-reembolso', {
        method: 'POST'
    })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'ok') {
                emReembolsoContactReason = 'refund_request';
                document.getElementById('em-reason').textContent = traducirContactReason('refund_request');
                document.getElementById('em-reclasificar-aviso').style.display = 'none';
                document.getElementById('em-form-reembolso').style.display = 'block';
            }
        })
        .catch(err => console.error('Error reclasificando:', err));
}

function enviarReembolsoInline() {
    const categoria = document.getElementById('em-reembolso-categoria').value;
    const monto = parseFloat(document.getElementById('em-reembolso-monto').value);
    const notas = document.getElementById('em-reembolso-notas').value;

    if (!monto || monto <= 0) {
        alert('Ingresa un monto válido.');
        return;
    }

    fetch('/reembolso/' + conversacionActualId)
        .then(res => res.json())
        .then(datos => {
            const precio = datos.precio || 0;
            return fetch('/reembolso/' + conversacionActualId + '/enviar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'reasonCategory=' + encodeURIComponent(categoria) +
                    '&amount=' + monto +
                    '&precio=' + precio +
                    '&agentNotes=' + encodeURIComponent(notas)
            });
        })
        .then(res => res.json())
        .then(data => {
            const form = document.getElementById('em-form-reembolso');
            if (data.status === 'ok') {
                form.innerHTML = '<p class="email-reembolso-confirmado">Solicitud enviada al supervisor.</p>';
            } else {
                alert('No se pudo enviar la solicitud. Intenta de nuevo.');
            }
        })
        .catch(err => {
            console.error('Error enviando reembolso:', err);
            alert('Error de conexión al enviar la solicitud.');
        });
}

function mostrarAvisoEnvioEn(contenedor, texto, tipo) {
    if (!contenedor) return;
    contenedor.textContent = texto;
    contenedor.className = 'reply-aviso reply-aviso-' + tipo;
    contenedor.style.display = 'block';
    setTimeout(() => { contenedor.style.display = 'none'; }, 4000);
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
                document.getElementById('sp-estado').textContent = traducirEstado(nuevoEstado);
                if (data.agente) {
                    document.getElementById('sp-agente').textContent = data.agente;
                }
                const rows = document.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    if (row.getAttribute('onclick').includes(conversacionActualId)) {
                        const badge = row.querySelector('.status-badge');
                        if (badge) {
                            badge.textContent = traducirEstado(nuevoEstado);
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

    const idPensando = 'pensando-' + Date.now();
    msgs.innerHTML += `<div class="msg msg-bot" id="${idPensando}">Escribiendo...</div>`;
    msgs.scrollTop = msgs.scrollHeight;

    fetch('/api/asistente/consultar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'mensaje=' + encodeURIComponent(text)
    })
        .then(res => res.json())
        .then(data => {
            const pensando = document.getElementById(idPensando);
            if (pensando) pensando.remove();
            msgs.innerHTML += `<div class="msg msg-bot">${data.respuesta}</div>`;
            msgs.scrollTop = msgs.scrollHeight;
        })
        .catch(err => {
            const pensando = document.getElementById(idPensando);
            if (pensando) pensando.remove();
            msgs.innerHTML += `<div class="msg msg-bot">Ocurrió un error, intenta de nuevo.</div>`;
            msgs.scrollTop = msgs.scrollHeight;
            console.error('Error en el asistente:', err);
        });
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
                mostrarAvisoEnvio('Mensaje enviado correctamente.', 'exito');
            } else if (data.status === 'sin_destinatario') {
                input.value = '';
                cargarMensajes(conversacionActualId);
                mostrarAvisoEnvio(data.mensaje || 'Sin destinatario. Verifica manualmente.', 'advertencia');
            } else {
                mostrarAvisoEnvio('No se pudo enviar la respuesta. Intenta de nuevo.', 'error');
            }
        })
        .catch(err => {
            console.error('Error enviando respuesta:', err);
            mostrarAvisoEnvio('Error de conexión al enviar la respuesta.', 'error');
        });
}

function mostrarAvisoEnvio(texto, tipo) {
    const contenedor = document.getElementById('reply-aviso');
    if (!contenedor) return;
    contenedor.textContent = texto;
    contenedor.className = 'reply-aviso reply-aviso-' + tipo;
    contenedor.style.display = 'block';
    setTimeout(() => { contenedor.style.display = 'none'; }, 4000);
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

// ==============================
// BUSCADOR + FILTRO FECHA
// ==============================

function toggleCalendario() {
    const dropdown = document.getElementById('calendario-dropdown');
    const btn = document.querySelector('.btn-filtro-fecha');
    const visible = dropdown.style.display !== 'none';
    dropdown.style.display = visible ? 'none' : 'block';
    btn.classList.toggle('activo', !visible);
}

function limpiarFechas() {
    document.getElementById('fecha-desde').value = '';
    document.getElementById('fecha-hasta').value = '';
    document.querySelector('.btn-filtro-fecha').classList.remove('activo');
    actualizarTextoBtnFecha();
}

function actualizarTextoBtnFecha() {
    const desde = document.getElementById('fecha-desde').value;
    const hasta = document.getElementById('fecha-hasta').value;
    const texto = document.getElementById('btn-fecha-texto');
    if (desde || hasta) {
        const d = desde ? new Date(desde + 'T00:00:00').toLocaleDateString('es-PE', {day:'2-digit', month:'short'}) : '...';
        const h = hasta ? new Date(hasta + 'T00:00:00').toLocaleDateString('es-PE', {day:'2-digit', month:'short'}) : '...';
        texto.textContent = d + ' → ' + h;
        document.querySelector('.btn-filtro-fecha').classList.add('activo');
    } else {
        texto.textContent = 'Fecha';
        document.querySelector('.btn-filtro-fecha').classList.remove('activo');
    }
}

function ejecutarBusqueda() {
    const q = document.getElementById('buscador-input').value.trim();
    const desde = document.getElementById('fecha-desde').value;
    const hasta = document.getElementById('fecha-hasta').value;

    const params = new URLSearchParams(window.location.search);

    if (q) params.set('q', q); else params.delete('q');
    if (desde) params.set('desde', desde); else params.delete('desde');
    if (hasta) params.set('hasta', hasta); else params.delete('hasta');

    // Cerrar dropdown
    document.getElementById('calendario-dropdown').style.display = 'none';

    window.location.href = '/quejas?' + params.toString();
}

// Cerrar calendario al hacer clic fuera
document.addEventListener('click', function(e) {
    const dropdown = document.getElementById('calendario-dropdown');
    const btn = document.querySelector('.btn-filtro-fecha');
    if (dropdown && !dropdown.contains(e.target) && btn && !btn.contains(e.target)) {
        dropdown.style.display = 'none';
        btn.classList.remove('activo');
    }
});

// Al cargar, reflejar fechas activas en el botón
document.addEventListener('DOMContentLoaded', function() {
    actualizarTextoBtnFecha();
    document.getElementById('fecha-desde').addEventListener('change', actualizarTextoBtnFecha);
    document.getElementById('fecha-hasta').addEventListener('change', actualizarTextoBtnFecha);
});