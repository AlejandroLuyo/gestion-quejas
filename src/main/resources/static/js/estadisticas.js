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
            const mapa = {
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
            backgroundColor: ['#378ADD', '#993C1D', '#1D9E75', '#BA7517', '#534AB7', '#D85A30', '#185FA5', '#639922'],
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