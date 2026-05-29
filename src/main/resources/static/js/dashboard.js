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
    fetch('/quejas/' + id + '/json')
        .then(res => res.json())
        .then(q => {
            document.getElementById('sp-name').textContent = q.nombreCliente;
            document.getElementById('sp-reason').textContent = q.contactReason;
            document.getElementById('sp-estado').textContent = q.estado;
            document.getElementById('sp-origen').textContent = q.origen || 'Ticket';
            document.getElementById('sp-fecha').textContent = q.fechaRegistro;
            document.getElementById('sp-descripcion').textContent = q.descripcion;
            document.getElementById('sp-sub').textContent = q.contactReason;
            document.getElementById('slide-panel').classList.add('open');
            document.getElementById('overlay').classList.add('show');
        });
}

function closePanel() {
    document.getElementById('slide-panel').classList.remove('open');
    document.getElementById('overlay').classList.remove('show');
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