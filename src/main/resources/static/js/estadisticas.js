// ===== GRÁFICOS =====
new Chart(document.getElementById('chart1'), {
    type: 'doughnut',
    data: {
        labels: ['Abiertas', 'Pendientes', 'Resueltas'],
        datasets: [{
            data: [abiertas, pendientes, resueltas],
            backgroundColor: ['#378ADD', '#BA7517', '#1D9E75'],
            borderWidth: 0
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } }
    }
});

new Chart(document.getElementById('chart2'), {
    type: 'bar',
    data: {
        labels: ['Email', 'WhatsApp', 'Ticket'],
        datasets: [{
            data: [porEmail, porWhatsapp, porTicket],
            backgroundColor: ['#185FA5', '#1D9E75', '#BA7517'],
            borderWidth: 0,
            borderRadius: 4
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
            y: { beginAtZero: true, ticks: { stepSize: 1 }, grid: { color: 'rgba(128,128,128,0.1)' } },
            x: { grid: { display: false } }
        }
    }
});

new Chart(document.getElementById('chart3'), {
    type: 'bar',
    data: {
        labels: contactReasons.map(function(r) {
            var mapa = {
                'payment_issues':          'Problemas de pago',
                'refund_request':          'Solicitud de reembolso',
                'status_information':      'Información de estado',
                'cx_modify':               'Modificación de orden',
                'deliverable_information': 'Información de entrega',
                'requirements_assistance': 'Asistencia de requisitos',
                'upload_support':          'Soporte de carga'
            };
            return mapa[r] || r;
        }),
        datasets: [{
            data: contactReasonData,
            backgroundColor: ['#378ADD','#993C1D','#1D9E75','#BA7517','#534AB7','#D85A30','#185FA5','#639922'],
            borderWidth: 0,
            borderRadius: 4
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        indexAxis: 'y',
        plugins: { legend: { display: false } },
        scales: {
            x: { beginAtZero: true, ticks: { stepSize: 1 }, grid: { color: 'rgba(128,128,128,0.1)' } },
            y: { grid: { display: false }, ticks: { font: { size: 11 } } }
        }
    }
});

// ===== ESTRELLAS CSAT =====
function renderizarEstrellas(valor) {
    var llenas = Math.floor(valor);
    var media  = (valor - llenas) >= 0.5 ? 1 : 0;
    var vacias = 5 - llenas - media;
    return '★'.repeat(llenas) + (media ? '½' : '') + '☆'.repeat(vacias);
}

document.querySelectorAll('.csat-estrellas, .csat-estrellas-agente').forEach(function(el) {
    var val = parseFloat(el.getAttribute('data-valor'));
    if (!isNaN(val)) el.textContent = renderizarEstrellas(val);
});

// ===== FILTRO RÁPIDO =====
function formatearFecha(fecha) {
    var y = fecha.getFullYear();
    var m = String(fecha.getMonth() + 1).padStart(2, '0');
    var d = String(fecha.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + d;
}

function aplicarPeriodoRapido(periodo, btn) {
    document.querySelectorAll('.qbtn').forEach(function(b) { b.classList.remove('activo'); });
    btn.classList.add('activo');

    var hoy = new Date();
    var desde = '', hasta = '';

    if (periodo === 'hoy') {
        desde = hasta = formatearFecha(hoy);
    } else if (periodo === 'semana') {
        var lunes = new Date(hoy);
        lunes.setDate(hoy.getDate() - ((hoy.getDay() + 6) % 7));
        desde = formatearFecha(lunes);
        hasta = formatearFecha(hoy);
    } else if (periodo === 'mes') {
        desde = formatearFecha(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
        hasta = formatearFecha(hoy);
    }

    document.getElementById('est-desde').value = desde;
    document.getElementById('est-hasta').value = hasta;

    if (periodo === 'todo') {
        window.location.href = '/estadisticas';
    } else {
        ejecutarBusquedaEst();
    }
}

function ejecutarBusquedaEst() {
    var desde = document.getElementById('est-desde').value;
    var hasta = document.getElementById('est-hasta').value;
    var params = [];
    if (desde) params.push('desde=' + desde);
    if (hasta) params.push('hasta=' + hasta);
    var url = '/estadisticas' + (params.length ? '?' + params.join('&') : '');
    window.location.href = url;
}

function limpiarFiltroEst() {
    window.location.href = '/estadisticas';
}

// Marcar botón activo al cargar si los params coinciden con un período rápido
(function() {
    if (!desdeParam && !hastaParam) return;
    var hoy = new Date();
    var hoyStr = formatearFecha(hoy);
    var lunes = new Date(hoy);
    lunes.setDate(hoy.getDate() - ((hoy.getDay() + 6) % 7));
    var lunesStr = formatearFecha(lunes);
    var primerMes = formatearFecha(new Date(hoy.getFullYear(), hoy.getMonth(), 1));

    if (desdeParam === hoyStr && hastaParam === hoyStr) {
        marcarQBtn('hoy');
    } else if (desdeParam === lunesStr && hastaParam === hoyStr) {
        marcarQBtn('semana');
    } else if (desdeParam === primerMes && hastaParam === hoyStr) {
        marcarQBtn('mes');
    }
})();

function marcarQBtn(periodo) {
    document.querySelectorAll('.qbtn').forEach(function(b) {
        if (b.getAttribute('data-periodo') === periodo) b.classList.add('activo');
    });
}

// Enter en los campos de fecha también dispara la búsqueda
document.getElementById('est-desde').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') ejecutarBusquedaEst();
});
document.getElementById('est-hasta').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') ejecutarBusquedaEst();
});