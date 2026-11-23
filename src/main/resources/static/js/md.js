/* Chuyển Markdown đơn giản (tiêu đề, danh sách, bảng, in đậm, trích dẫn) thành HTML an toàn.
 * Dùng: <pre class="md-src" hidden>...</pre><div class="md-out"></div> */
(function () {
  function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function inline(s) {
    return esc(s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/`(.+?)`/g, '<code>$1</code>');
  }
  function render(text) {
    var html = '', inList = false, inTable = false;
    function close() {
      if (inList) { html += '</ul>'; inList = false; }
      if (inTable) { html += '</tbody></table>'; inTable = false; }
    }
    text.split('\n').forEach(function (line) {
      var t = line.trim(), m;
      if (!t) { close(); return; }
      if ((m = t.match(/^(#{1,4})\s+(.*)/))) {
        close();
        var n = Math.min(m[1].length + 1, 5);
        html += '<h' + n + '>' + inline(m[2]) + '</h' + n + '>';
        return;
      }
      if (t.charAt(0) === '|') {
        if (/^\|[\s:|-]+\|?$/.test(t)) return; // dòng phân cách của bảng
        var cells = t.replace(/^\||\|$/g, '').split('|');
        if (!inTable) { close(); html += '<table><tbody>'; inTable = true; }
        html += '<tr>' + cells.map(function (c) { return '<td>' + inline(c.trim()) + '</td>'; }).join('') + '</tr>';
        return;
      }
      if ((m = t.match(/^[-*]\s+(.*)/))) {
        if (!inList) { close(); html += '<ul>'; inList = true; }
        html += '<li>' + inline(m[1]) + '</li>';
        return;
      }
      close();
      if (t.charAt(0) === '>') { html += '<blockquote>' + inline(t.replace(/^>\s?/, '')) + '</blockquote>'; return; }
      html += '<p>' + inline(t) + '</p>';
    });
    close();
    return html;
  }
  document.querySelectorAll('.md-src').forEach(function (src) {
    var out = src.nextElementSibling;
    if (out) out.innerHTML = render(src.textContent);
  });
})();
