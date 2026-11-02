// Execute our standalone reports with a minimal DOM to catch selector/runtime errors.
// This checks data/JavaScript execution, not browser layout or visual appearance.
const fs = require('fs');
for (const path of process.argv.slice(2)) {
  const html = fs.readFileSync(path, 'utf8');
  const nodes = new Map();
  class Element {
    constructor() { this.children = []; this.value = ''; this.listeners = {}; }
    append(...items) { this.children.push(...items); }
    replaceChildren(...items) { this.children = items; }
    addEventListener(name, fn) { this.listeners[name] = fn; }
  }
  for (const tag of html.matchAll(/<[^>]+\bid="([^"]+)"[^>]*>/g)) {
    const element = new Element();
    const value = tag[0].match(/\bvalue="([^"]*)"/);
    if (value) element.value = value[1];
    nodes.set(tag[1], element);
  }
  const document = { getElementById: id => nodes.get(id) || null, createElement: () => new Element() };
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const check = `
    let rendered = 0;
    if (document.getElementById('example') && document.getElementById('phase')) {
      for (const phase of ['development','check']) {
        document.getElementById('phase').value = phase;
        for (const limit of ['tail','maximum']) {
          document.getElementById('limit').value = limit;
          render(); rendered++;
          for (const e of D.examples) {
            document.getElementById('example').value = e.id;
            renderExample(); rendered++;
          }
        }
      }
    } else if (document.getElementById('event')) {
      for (const e of D.events) {
        document.getElementById('event').value = e.first;
        renderEvent(); rendered++;
      }
      for (const c of D.lower) {
        document.getElementById('lowerCap').value = c.cap;
        renderLower(); rendered++;
      }
    } else if (document.getElementById('experiment')) {
      for (const e of D.experiments) {
        document.getElementById('experiment').value = e.id;
        selectExperiment();
        for (const c of D.capillaries.filter(c => c.experiment === e.id)) {
          document.getElementById('capillary').value = c.cap;
          render(); rendered++;
        }
      }
    } else {
      for (const c of D.capillaries) {
        document.getElementById('cap').value = c.cap;
        document.getElementById('first').value = 0;
        document.getElementById('last').value = c.rows - 1;
        render(); rendered++;
      }
    }
    return rendered;
  `;
  const count = new Function('document', script + check)(document);
  console.log(`${path}: ${count} report views executed successfully (layout not tested)`);
}
