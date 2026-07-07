let reembolsoActualId = null;
let reembolsoPrecio = 0;

function abrirDetalle(reembolsoId) {
    reembolsoActualId = reembolsoId;
    document.getElementById('detalle-panel').classList.add('open');
    document.getElementById('overlay').classList.add('show');

    fetch('/reembolso/detalle/' + reembolsoId)
        .then(res => res.json())
        .then(data => renderDetalle(data))
        .catch(err => console.error('Error cargando detalle:', err));
}

function cerrarDetalle() {
    document.getElementById('detalle-panel').classList.remove('open');
    document.getElementById('overlay').classList.remove('show');
    reembolsoActualId = null;
}

function traducirMotivo(key) {
    const map = {
        'amenaza_legal': 'Amenaza de acción legal',
        'redes_sociales': 'Publicación en redes sociales',
        'error_empresa': 'Error comprobado de la empresa'
    };
    return map[key] || key || '-';
}

function traducirEstado(r) {
    if (r.botRefundStatus === 'pendiente_agente') return 'Pendiente de revisión (agente)';
    if (r.botRefundStatus === 'pendiente_supervisor') return 'Pendiente de aprobación';
    if (r.refundResult === 'aprobado') return 'Aprobado';
    if (r.refundResult === 'denegado') return 'Denegado por agente';
    if (r.refundResult === 'rechazado_supervisor') return 'Rechazado por supervisor';
    return '-';
}

function renderDetalle(data) {
    reembolsoPrecio = data.precio || 0;
    const conv = data.conversacion;

    document.getElementById('dp-titulo').textContent = (conv?.orderId || '?') + ' — Reembolso';
    document.getElementById('dp-sub').textContent = (conv?.agente || '-') + ' · ' + (conv?.fecha || '-');

    const esPendiente = data.botRefundStatus === 'pendiente_supervisor';
    const esCerrado   = data.botRefundStatus === 'cerrado';

    let html = `
        <div class="dp-seccion">
            <div class="dp-grid">
                <div>
                    <div class="dp-label">Order ID</div>
                    <div class="dp-valor">${conv?.orderId || '-'}</div>
                </div>
                <div>
                    <div class="dp-label">Agente</div>
                    <div class="dp-valor">${conv?.agente || '-'}</div>
                </div>
                <div>
                    <div class="dp-label">Motivo</div>
                    <div class="dp-valor">${traducirMotivo(data.refundReasonCategory)}</div>
                </div>
                <div>
                    <div class="dp-label">Estado</div>
                    <div class="dp-valor">${traducirEstado(data)}</div>
                </div>
                <div>
                    <div class="dp-label">Monto solicitado</div>
                    <div class="dp-valor">S/ ${data.refundAmount?.toFixed(2) || '-'} (${data.refundPercent || '-'}%)</div>
                </div>
                <div>
                    <div class="dp-label">Precio original</div>
                    <div class="dp-valor">S/ ${reembolsoPrecio.toFixed(2)}</div>
                </div>
            </div>
            ${data.agentNotes ? `
            <div>
                <div class="dp-label" style="margin-bottom:4px;">Notas del agente</div>
                <div class="dp-notas">${data.agentNotes}</div>
            </div>` : ''}
            ${data.supervisorNotes ? `
            <div>
                <div class="dp-label" style="margin-bottom:4px;">Nota del supervisor</div>
                <div class="dp-notas">${data.supervisorNotes}</div>
            </div>` : ''}
        </div>`;

    if (esPendiente) {
        html += `
        <div class="dp-acciones">
            <div>
                <label class="dp-label" style="margin-bottom:4px;">Monto final a devolver (S/)</label>
                <input type="number" id="dp-monto" value="${data.refundAmount?.toFixed(2)}"
                       step="0.01" style="width:100%; font-size:13px; padding:7px 10px; border-radius:8px; border:1px solid #e2e8f0;"/>
            </div>
            <div>
                <label class="dp-label" style="margin-bottom:4px;">Nota del supervisor</label>
                <textarea id="dp-nota" rows="3" placeholder="Nota para el agente y/o finanzas..."
                          style="width:100%; font-size:13px; padding:7px 10px; border-radius:8px; border:1px solid #e2e8f0; font-family:inherit; resize:none;"></textarea>
            </div>
            <button onclick="aprobarReembolsoDashboard()"
                    style="background:#16a34a; color:#fff; border:none; font-size:13px; font-weight:500; padding:9px; border-radius:8px; cursor:pointer;">
                Aprobar y enviar a finanzas
            </button>
            <button onclick="rechazarReembolsoDashboard()"
                    style="background:transparent; border:1px solid #dc2626; color:#dc2626; font-size:13px; font-weight:500; padding:9px; border-radius:8px; cursor:pointer;">
                Rechazar solicitud
            </button>
        </div>`;
    } if (esCerrado) {
        const textos = {
            'aprobado': '✅ Reembolso aprobado y enviado a finanzas.',
            'denegado': '❌ Reembolso denegado definitivamente.',
        };
        html += `<div class="dp-resultado">${textos[data.refundResult] || 'Caso cerrado.'}</div>`;
    } else if (data.botRefundStatus === 'rechazado_supervisor') {
        html += `<div class="dp-resultado" style="color:#92400e;">
        ⛔ Solicitud rechazada por el supervisor. El agente debe reintentar o cerrar desde el panel de conversación.
    </div>`;
    }

    document.getElementById('dp-body').innerHTML = html;
}

function aprobarReembolsoDashboard() {
    const monto = parseFloat(document.getElementById('dp-monto')?.value);
    const nota  = document.getElementById('dp-nota')?.value || '';
    if (!monto || monto <= 0) { alert('Ingresa un monto válido.'); return; }
    if (!confirm('¿Confirmas la aprobación y envío a finanzas?')) return;

    fetch('/reembolso/' + reembolsoActualId + '/aprobar-por-id', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `montoFinal=${monto}&precio=${reembolsoPrecio}&supervisorNotes=${encodeURIComponent(nota)}`
    })
        .then(res => res.json())
        .then(data => { if (data.status === 'ok') location.reload(); })
        .catch(err => console.error(err));
}

function rechazarReembolsoDashboard() {
    const nota = document.getElementById('dp-nota')?.value || '';
    if (!confirm('¿Confirmas el rechazo de esta solicitud?')) return;

    fetch('/reembolso/' + reembolsoActualId + '/rechazar-por-id', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `supervisorNotes=${encodeURIComponent(nota)}`
    })
        .then(res => res.json())
        .then(data => { if (data.status === 'ok') location.reload(); })
        .catch(err => console.error(err));
}

// ==============================
// BUSCADOR + FILTRO FECHA
// ==============================

function toggleCalendarioReembolso() {
    const dropdown = document.getElementById('calendario-dropdown');
    const btn = document.querySelector('.btn-filtro-fecha');
    const visible = dropdown.style.display !== 'none';
    dropdown.style.display = visible ? 'none' : 'block';
    btn.classList.toggle('activo', !visible);
}

function limpiarFechasReembolso() {
    document.getElementById('fecha-desde').value = '';
    document.getElementById('fecha-hasta').value = '';
    document.querySelector('.btn-filtro-fecha').classList.remove('activo');
    actualizarTextoBtnFechaReembolso();
}

function actualizarTextoBtnFechaReembolso() {
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

function ejecutarBusquedaReembolso() {
    const q = document.getElementById('buscador-input').value.trim();
    const desde = document.getElementById('fecha-desde').value;
    const hasta = document.getElementById('fecha-hasta').value;

    const params = new URLSearchParams(window.location.search);

    if (q) params.set('q', q); else params.delete('q');
    if (desde) params.set('desde', desde); else params.delete('desde');
    if (hasta) params.set('hasta', hasta); else params.delete('hasta');

    document.getElementById('calendario-dropdown').style.display = 'none';

    window.location.href = '/reembolso/lista?' + params.toString();
}

// Cerrar calendario al hacer clic fuera
document.addEventListener('click', function(e) {
    const dropdown = document.getElementById('calendario-dropdown');
    const btn = document.querySelector('.btn-filtro-fecha');
    if (dropdown && !dropdown.contains(e.target) && btn && !btn.contains(e.target)) {
        dropdown.style.display = 'none';
        if (btn) btn.classList.remove('activo');
    }
});

// Al cargar, reflejar fechas activas en el botón
document.addEventListener('DOMContentLoaded', function() {
    actualizarTextoBtnFechaReembolso();
    const desde = document.getElementById('fecha-desde');
    const hasta = document.getElementById('fecha-hasta');
    if (desde) desde.addEventListener('change', actualizarTextoBtnFechaReembolso);
    if (hasta) hasta.addEventListener('change', actualizarTextoBtnFechaReembolso);
});