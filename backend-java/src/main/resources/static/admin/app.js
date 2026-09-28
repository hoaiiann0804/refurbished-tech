const $ = (selector, root = document) => root.querySelector(selector);
const state = { token: null, user: null, epoch: 0, page: 0, products: [], devices: [], cart: new Map(), checkout: null };
const titles = { products: 'Model thiết bị', inventory: 'Kho & kiểm định', checkout: 'Bán tại quầy', orders: 'Đơn bán hàng', online: 'Giữ máy & giao hàng', audit: 'Lịch sử thao tác', users: 'Cấp tài khoản', account: 'Tài khoản của tôi' };
const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
const date = value => value ? new Date(value).toLocaleString('vi-VN') : '—';
function el(tag, text, cls) { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; if (cls) node.className = cls; return node; }
function notice(message, error = false) { const box = $('#notice'); box.textContent = message; box.className = error ? 'error' : ''; box.hidden = false; clearTimeout(notice.timer); notice.timer = setTimeout(() => { box.hidden = true; }, 10000); }
function logout() { state.epoch++; state.token = null; state.user = null; state.cart.clear(); state.checkout = null; state.reservationAttempt = null; state.paymentAttempts = null; state.customerName = ''; state.devices = []; state.products = []; $('#content').replaceChildren(); $('#detail').close(); $('#detail-content').replaceChildren(); $('#workspace').hidden = true; $('#login').hidden = false; $('#login-form').reset(); }
async function api(path, method = 'GET', body, headers = {}) {
  const epoch = state.epoch;
  let response;
  try { response = await fetch(path, { method, headers: { 'Content-Type': 'application/json', ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}), ...headers }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000), credentials: 'omit' }); }
  catch { throw new Error('Chưa xác định được kết quả do mất kết nối. Với bán hàng, giữ nguyên nội dung và thử lại để nhận đơn cũ.'); }
  // A response from the previous session must never repopulate another user's screen.
  if (epoch !== state.epoch) throw new Error('Phiên làm việc đã thay đổi.');
  const data = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    if (response.status === 401 && state.token) logout();
    const messages = { 401: 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.', 403: 'Bạn không có quyền thực hiện thao tác này.', 404: 'Không tìm thấy dữ liệu hoặc chức năng chưa được bật.', 409: 'Dữ liệu đã thay đổi hoặc thao tác không hợp lệ. Hãy kiểm tra trạng thái mới nhất.' };
    const fields = Object.entries(data.fieldErrors || {}).map(([key, value]) => `${key}: ${value}`).join('\n');
    const error = new Error([messages[response.status] || data.message || 'Không xử lý được yêu cầu.', fields, response.status === 409 ? data.message : ''].filter(Boolean).join('\n'));
    error.status = response.status; throw error;
  }
  return data;
}
function action(label, work, cls = 'secondary') { const button = el('button', label, cls); button.type = 'button'; button.addEventListener('click', () => run(button, work)); return button; }
async function run(button, work) { button.disabled = true; try { await work(); } catch (error) { notice(error.message, true); } finally { button.disabled = false; } }
function form(title, fields, submit, label = 'Lưu') {
  const node = el('form', undefined, 'card'); node.append(el('h2', title));
  for (const [name, caption, type = 'text', options = {}] of fields) {
    const wrapper = el('label', caption); let input;
    if (type === 'select') { input = el('select'); for (const [value, text] of options.choices || []) { const option = el('option', text); option.value = value; input.append(option); } }
    else { input = el(type === 'textarea' ? 'textarea' : 'input'); if (type !== 'textarea') input.type = type; }
    input.name = name; input.required = !options.optional;
    for (const [key, value] of Object.entries(options)) if (!['choices', 'optional'].includes(key)) input[key] = value;
    wrapper.append(input); node.append(wrapper);
  }
  const button = el('button', label); node.append(button);
  node.addEventListener('submit', event => { event.preventDefault(); run(button, () => submit(Object.fromEntries(new FormData(node)), node)); });
  return node;
}
function table(headers, rows) {
  const wrapper = el('div', undefined, 'table-wrap'); if (!rows.length) { wrapper.append(el('p', 'Chưa có dữ liệu phù hợp.', 'empty')); return wrapper; }
  const node = el('table'), head = el('thead'), tr = el('tr'), body = el('tbody'); headers.forEach(h => tr.append(el('th', h))); head.append(tr);
  rows.forEach(cells => { const row = el('tr'); cells.forEach(value => { const cell = el('td'); cell.append(value instanceof Node ? value : document.createTextNode(String(value ?? '—'))); row.append(cell); }); body.append(row); });
  node.append(head, body); wrapper.append(node); return wrapper;
}
function buttons(...items) { const node = el('div'); node.append(...items); return node; }
function card(title) { const node = el('section', undefined, 'card'); node.append(el('h2', title)); return node; }
function pagination(container, data) {
  const node = el('div', undefined, 'pagination'); const prev = action('← Trước', async () => { state.page--; await render(); }), next = action('Sau →', async () => { state.page++; await render(); });
  prev.disabled = data.page === 0; next.disabled = data.page + 1 >= data.totalPages; node.append(prev, el('span', `${data.totalElements} kết quả · Trang ${data.page + 1}`), next); container.append(node);
}
function modal(title, content) { $('#detail-content').replaceChildren(el('h2', title), content); $('#detail').showModal(); }
async function productOptions() { const data = await api('/api/products?active=true&size=100'); return data.items.map(p => [p.id, `${p.brand} · ${p.name} (${p.modelCode})`]); }
const currentTab = () => location.hash.slice(1).split('?')[0] || 'products';
function filterQuery() { return new URLSearchParams(location.hash.split('?')[1] || ''); }
function filters(fields) { const node = form('Bộ lọc', fields.map(([key, label, type = 'text', choices]) => [key, label, type, { optional: true, ...(choices ? { choices } : {}), value: filterQuery().get(key) || '' }]), async values => { const query = new URLSearchParams(Object.entries(values).filter(([,value]) => value)); state.page = 0; location.hash = `${currentTab()}?${query}`; }, 'Áp dụng'); node.className = 'toolbar'; $('h2', node).remove(); return node; }
async function products(root) {
  const data = await api(`/api/products?page=${state.page}&size=15`); const left = card('Danh mục model');
  left.append(table(['Model', 'Hãng', 'Trạng thái', ''], data.items.map(p => [p.name, p.brand, p.active ? 'Đang kinh doanh' : 'Ngừng nhập', state.user.role === 'ADMIN' ? action('Chỉnh sửa', () => {
    modal('Cập nhật model', form('Thông tin model', [['name','Tên','text',{value:p.name,maxLength:200}],['brand','Hãng','text',{value:p.brand,maxLength:100}],['specificationSummary','Cấu hình','textarea',{value:p.specificationSummary || '',optional:true,maxLength:2000}],['active','Cho nhập kho','select',{choices:[['true','Có'],['false','Không']],value:String(p.active)}]], async values => { await api(`/api/products/${p.id}`,'PUT',{...values,active:values.active==='true'}); $('#detail').close(); await render(); }));
  }) : 'Chỉ xem']))); pagination(left,data); root.append(left);
  if (state.user.role === 'ADMIN') root.append(form('Thêm model', [['modelCode','Mã model','text',{maxLength:100}],['name','Tên thiết bị','text',{maxLength:200}],['brand','Hãng','text',{maxLength:100}],['specificationSummary','Cấu hình','textarea',{optional:true,maxLength:2000}]], async values => { await api('/api/products','POST',values); notice('Đã tạo model. Bạn có thể nhập máy vào kho.'); await render(); }, 'Tạo model'));
}
async function inventory(root) {
  const query = filterQuery(); query.set('page',state.page); query.set('size','15'); const data = await api(`/api/device-units?${query}`); const left = card('Thiết bị theo serial');
  left.append(filters([['serialNumber','Serial chính xác'],['status','Trạng thái','select',[['','Tất cả'],...['RECEIVED','INSPECTING','AVAILABLE','RESERVED','SOLD','REJECTED'].map(s=>[s,s])]]]));
  left.append(table(['Serial','Trạng thái','Giá bán','Thao tác'],data.items.map(d=>[d.serialNumber,el('span',d.status,'badge'),d.salePrice?money(d.salePrice):'Chưa định giá',buttons(
    action('Chi tiết',()=>modal(d.serialNumber,table(['Thuộc tính','Giá trị'],[['Grade',d.grade],['Pin',d.batteryHealth ?? d.batteryHealthUnavailableReason],['Ghi chú kiểm định',d.inspectionNotes],['Cập nhật',date(d.updatedAt)]]))),
    ...(d.status==='RECEIVED'?[action('Kiểm định',async()=>{await api(`/api/device-units/${d.id}/start-inspection`,'POST');await render();})]:[]),
    ...(d.status==='INSPECTING'?[action('Kết quả',()=>inspection(d))]:[]),
    ...(d.status==='SOLD'?[action('Bảo hành',()=>warranty(d))]:[])
  )])));pagination(left,data);root.append(left);
  root.append(form('Nhập thiết bị', [['productId','Model','select',{choices:await productOptions()}],['serialNumber','Serial','text',{maxLength:100}]],async values=>{await api('/api/device-units','POST',values);notice('Đã nhập máy. Bắt đầu kiểm định trước khi bán.');await render();},'Nhập kho'));
}
function inspection(device) {
  modal(device.serialNumber,form('Kết quả kiểm định', [['passed','Kết quả','select',{choices:[['true','Đạt'],['false','Không đạt']]}],['grade','Grade','select',{choices:[['A','A'],['B','B'],['C','C']]}],['batteryHealth','Pin (%)','number',{min:0,max:100,optional:true}],['batteryHealthUnavailableReason','Lý do không đo được pin','textarea',{optional:true,maxLength:1000}],['salePrice','Giá bán (VND)','number',{min:0.01,step:0.01,optional:true}],['inspectionNotes','Bằng chứng / ghi chú','textarea',{maxLength:2000}]],async values=>{
    if(values.batteryHealth!=='' && values.batteryHealthUnavailableReason.trim())throw new Error('Chỉ nhập chỉ số pin hoặc lý do không đo được.');
    await api(`/api/device-units/${device.id}/complete-inspection`,'POST',{...values,passed:values.passed==='true',batteryHealth:values.batteryHealth===''?null:Number(values.batteryHealth),salePrice:values.salePrice===''?null:Number(values.salePrice),batteryHealthUnavailableReason:values.batteryHealthUnavailableReason||null});$('#detail').close();await render();
  },'Lưu kiểm định'));
}
async function warranty(device) {
  try {const data=await api(`/api/warranties/device-unit/${device.id}`);modal('Bảo hành · '+device.serialNumber,table(['Nội dung','Giá trị'],[['Bắt đầu',data.startsOn],['Kết thúc',data.endsOn],['Số tháng',data.durationMonths]]));}
  catch(error){if(error.status!==404)throw error;modal('Cấp bảo hành · '+device.serialNumber,form('Thời hạn bảo hành',[['durationMonths','Số tháng','number',{min:1,max:36,value:12}]],async values=>{await api('/api/warranties','POST',{deviceUnitId:device.id,durationMonths:Number(values.durationMonths)});$('#detail').close();notice('Đã cấp bảo hành.');}));}
}
async function checkout(root) {
  const data=await api(`/api/device-units?status=AVAILABLE&page=${state.page}&size=15`);const left=card('Máy đủ điều kiện bán');
  left.append(table(['Serial','Giá',''],data.items.map(d=>[d.serialNumber,money(d.salePrice),action(state.cart.has(d.id)?'Bỏ chọn':'Chọn bán',async()=>{if(state.cart.has(d.id))state.cart.delete(d.id);else state.cart.set(d.id,d);await render();})])));pagination(left,data);root.append(left);
  const right=form('Xác nhận bán',[['customerName','Tên khách','text',{maxLength:200,value:state.customerName||''}]],async values=>{
    state.customerName=values.customerName;
    if(!state.cart.size)throw new Error('Chọn ít nhất một máy AVAILABLE.');
    const body={customerName:values.customerName,deviceUnitIds:[...state.cart.keys()].sort()};const fingerprint=JSON.stringify(body);
    // Never issue a fresh key for an unchanged retry after a lost response.
    if(state.checkout?.fingerprint!==fingerprint)state.checkout={fingerprint,key:crypto.randomUUID()};
    const result=await api('/api/orders/checkout','POST',body,{'Idempotency-Key':state.checkout.key});state.cart.clear();state.checkout=null;state.customerName='';notice('Đã hoàn tất đơn: '+money(result.totalAmount));await render();
  },'Chốt bán');
  const selected=el('div');for(const d of state.cart.values())selected.append(el('div',`${d.serialNumber} · ${money(d.salePrice)}`,'cart-item'));right.insertBefore(selected,right.children[1]);$('input',right).addEventListener('input',e=>state.customerName=e.target.value);root.append(right);
}
async function orders(root) {
  root.className='stack';const query=filterQuery();query.set('page',state.page);query.set('size','15');const data=await api(`/api/orders?${query}`);const node=card('Đơn đã chốt');node.append(filters([['customerName','Tên khách']]));
  node.append(table(['Khách hàng','Thời gian','Tổng tiền',''],data.items.map(o=>[o.customerName,date(o.createdAt),money(o.totalAmount),action('Chi tiết',async()=>{const detail=await api(`/api/orders/${o.id}`);modal('Đơn của '+detail.customerName,table(['Serial','Giá snapshot',''],detail.items.map(i=>[i.serialNumber,money(i.unitPrice),action('Bảo hành',async()=>{$('#detail').close();await warranty({id:i.deviceUnitId,serialNumber:i.serialNumber});})])));})])));pagination(node,data);root.append(node);
}
async function online(root) {
  state.onlineCapabilities = await api('/api/online/reservations/capabilities');
  const data=await api(`/api/online/reservations?page=${state.page}&size=15`);const left=card('Đơn giữ máy');left.append(el('p','Giữ máy 15 phút. Thanh toán sandbox chỉ mô phỏng, không thu hoặc hoàn tiền thật.','banner'));
  left.append(table(['Khách / thời hạn','Giữ máy / tiền','Giao hàng',''],data.items.map(r=>[`${r.customerName} · ${date(r.expiresAt)}`,`${r.status} / ${r.paymentStatus}`,r.shipmentStatus,action('Xử lý',()=>reservationDetail(r))])));pagination(left,data);root.append(left);
  const devices=await api('/api/device-units?status=AVAILABLE&size=100');root.append(form('Giữ máy cho khách',[['deviceUnitId','Máy AVAILABLE','select',{choices:devices.items.map(d=>[d.id,`${d.serialNumber} · ${money(d.salePrice)}`])}],['customerName','Tên khách','text',{maxLength:200}],['shippingAddress','Địa chỉ nhận','textarea',{maxLength:500}]],async values=>{
    const fingerprint=JSON.stringify(values);if(state.reservationAttempt?.fingerprint!==fingerprint)state.reservationAttempt={fingerprint,id:crypto.randomUUID()};
    await api('/api/online/reservations','POST',{...values,requestId:state.reservationAttempt.id});state.reservationAttempt=null;notice('Đã giữ máy 15 phút.');await render();
  },'Giữ máy'));
}
function reservationDetail(r) {
  const node=el('div');node.append(table(['Thông tin','Giá trị'],[['Khách',r.customerName],['Địa chỉ',r.shippingAddress],['Số tiền',money(r.amount)],['Trạng thái tiền',r.paymentStatus],['Tracking',r.trackingNumber]]));
  const doAction=async(suffix,body)=>{await api(`/api/online/reservations/${r.id}/${suffix}`,'POST',body);$('#detail').close();await render();};
  if(r.status==='ACTIVE')node.append(action('Hủy giữ máy',()=>doAction('cancel')));
  if(state.user.role==='ADMIN' && state.onlineCapabilities?.sandboxPayments) {
    for(const [kind,label,allowed] of [['sandbox-payment','Ghi thanh toán sandbox',r.paymentStatus==='UNPAID'],['sandbox-refund','Hoàn tiền sandbox',['PAID','LATE_PAYMENT'].includes(r.paymentStatus)]]) if(allowed)node.append(action(label,()=>{
      const key=`${r.id}:${kind}`;state.paymentAttempts??=new Map();if(!state.paymentAttempts.has(key))state.paymentAttempts.set(key,crypto.randomUUID());
      return doAction(kind,{eventId:state.paymentAttempts.get(key),amount:r.amount,currency:r.currency});
    }));
  }
  if(r.paymentStatus==='PAID'&&r.shipmentStatus!=='DELIVERED')node.append(form('Cập nhật giao hàng',[['trackingNumber','Mã vận đơn','text',{value:r.trackingNumber||'',maxLength:100}]],values=>doAction('shipment',{...values,status:r.shipmentStatus==='PENDING'?'SHIPPED':'DELIVERED'}),r.shipmentStatus==='PENDING'?'Đã giao đơn vị vận chuyển':'Đã giao khách'));
  modal('Xử lý giữ máy',node);
}
async function audit(root) {root.className='stack';const query=filterQuery();query.set('page',state.page);query.set('size','20');const data=await api(`/api/audit-events?${query}`);const node=card('Nhật ký nghiệp vụ');node.append(filters([['action','Hành động'],['targetId','ID đối tượng']]));node.append(table(['Thời gian','Người thực hiện','Hành động','Đối tượng'],data.items.map(e=>[date(e.occurredAt),e.actorUserId||e.actorKind,e.action,`${e.targetType} · ${e.targetId}`])));pagination(node,data);root.append(node);}
async function users(root) { root.append(form('Cấp tài khoản nhân viên',[['email','Email','email'],['displayName','Tên hiển thị','text',{maxLength:200}],['password','Mật khẩu ban đầu','password',{minLength:12,maxLength:72}],['role','Quyền','select',{choices:[['STAFF','Nhân viên'],['ADMIN','Quản trị viên']]}]],async(values,node)=>{await api('/api/users','POST',values);node.reset();notice('Đã cấp tài khoản.');}));root.append(el('p','Danh sách, khóa/mở và bảo vệ ADMIN cuối cùng thuộc bước 1B, chưa có trong màn hình này.','banner')); }
async function account(root) {root.append(form('Đổi mật khẩu',[['currentPassword','Mật khẩu hiện tại','password'],['newPassword','Mật khẩu mới','password',{minLength:12,maxLength:72}]],async values=>{await api('/api/auth/change-password','POST',values);logout();notice('Đã đổi mật khẩu. Hãy đăng nhập lại.');}));const node=card('Phiên đăng nhập');node.append(el('p','Đăng xuất tất cả sẽ thu hồi token trên mọi thiết bị.','muted'),action('Đăng xuất tất cả',async()=>{await api('/api/auth/logout-all','POST');logout();}));root.append(node);}
let rendering=0;
async function render() {
  if(!state.token)return;const tab=currentTab(), generation=++rendering;
  if(!titles[tab]){location.hash='products';return;}
  if(['audit','users'].includes(tab)&&state.user.role!=='ADMIN'){location.hash='inventory';return;}
  $('#title').textContent=titles[tab];document.querySelectorAll('nav a').forEach(a=>a.classList.toggle('active',a.hash==='#'+tab));
  const root=el('div',undefined,'grid');$('#content').replaceChildren(el('p','Đang tải…','muted'));
  try {await ({products,inventory,checkout,orders,online,audit,users,account}[tab])(root);if(generation===rendering&&state.token)$('#content').replaceChildren(root);}catch(error){if(generation===rendering&&state.token)$('#content').replaceChildren(el('p',error.message,'banner'));notice(error.message,true);}
}
$('#login-form').addEventListener('submit',event=>{event.preventDefault();run($('#login-form button'),async()=>{const data=await api('/api/auth/login','POST',Object.fromEntries(new FormData(event.target)));state.token=data.accessToken;state.user=data.user;state.page=0;$('#login-form').reset();$('#login').hidden=true;$('#workspace').hidden=false;$('#identity').textContent=`${data.user.displayName} · ${data.user.role}`;const nav=$('nav');nav.replaceChildren();for(const [key,title]of Object.entries(titles)){if(['audit','users'].includes(key)&&data.user.role!=='ADMIN')continue;const link=el('a',title);link.href='#'+key;nav.append(link);}await render();});});
$('#logout').onclick=logout;$('#refresh').onclick=()=>render();$('#close-dialog').onclick=()=>$('#detail').close();window.addEventListener('hashchange',()=>{state.page=0;render();});
