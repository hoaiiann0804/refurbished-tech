const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(path.resolve(__dirname, '../.run/browser-runner/node_modules/playwright'));
const assert = require('node:assert/strict');
const env = process.env;
const base = env.POSTMAN_BASE_URL;
const run = env.POSTMAN_RUN_ID;
const edge = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
async function login(page, email, password) {
  await page.goto(base + '/admin/index.html');
  await page.getByLabel('Email', {exact:true}).fill(email);
  await page.getByLabel('Mật khẩu', {exact:true}).fill(password);
  await page.getByRole('button', {name:'Đăng nhập',exact:true}).click();
  await page.locator('#workspace').waitFor({state:'visible'});
}
async function navigate(page, title) {
  await page.getByRole('link', {name:title,exact:true}).click();
  await page.getByRole('heading', {name:title,exact:true}).waitFor();
}
(async () => {
 const browser = await chromium.launch({headless:true, ...(fs.existsSync(edge)?{executablePath:edge}:{})});
 try {
  const admin = await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[]; admin.on('pageerror',error=>errors.push(error.message));
  await login(admin, env.POSTMAN_EMAIL, env.POSTMAN_PASSWORD);
  await admin.getByLabel('Mã model', {exact:true}).fill('PM-'+run);
  await admin.getByLabel('Tên thiết bị',{exact:true}).fill('Browser '+run);
  await admin.getByLabel('Hãng',{exact:true}).fill('Test');
  await admin.getByRole('button',{name:'Tạo model',exact:true}).click();
  await admin.getByRole('cell',{name:'Browser '+run,exact:true}).waitFor();
  await navigate(admin,'Cấp tài khoản');
  await admin.locator('#content').getByLabel('Email',{exact:true}).fill(env.BROWSER_STAFF_EMAIL);
  await admin.getByLabel('Tên hiển thị',{exact:true}).fill('Browser Staff');
  await admin.getByLabel('Mật khẩu ban đầu',{exact:true}).fill(env.BROWSER_STAFF_PASSWORD);
  await admin.getByRole('button',{name:'Lưu',exact:true}).click();
  await admin.getByText('Đã cấp tài khoản.',{exact:true}).waitFor();
  const staff = await browser.newPage({viewport:{width:1100,height:1000}});
  staff.on('pageerror',error=>errors.push(error.message));
  await login(staff,env.BROWSER_STAFF_EMAIL,env.BROWSER_STAFF_PASSWORD);
  assert.equal(await staff.getByRole('link',{name:'Cấp tài khoản',exact:true}).count(),0);
  await navigate(staff,'Kho & kiểm định');
  // The page title is set before asynchronous product options finish loading.
  // Wait for the actual form so the E2E test exercises a ready screen, not a race in rendering.
  // Select stable field names instead of translated UI text. Translation must not make a test brittle.
  const receiveForm = staff.locator('#content form').filter({has: staff.locator('select[name="productId"]')});
  await receiveForm.locator('select[name="productId"]').waitFor({state:'visible'});
  await receiveForm.locator('select[name="productId"]').selectOption({label:`Test · Browser ${run} (PM-${run.toUpperCase()})`});
  await receiveForm.locator('input[name="serialNumber"]').fill('UI-'+run);
  await receiveForm.getByRole('button').click();
  const serial=('UI-'+run).toUpperCase();
  const row=staff.getByRole('row').filter({has:staff.getByRole('cell',{name:serial,exact:true})});
  await row.getByRole('button',{name:'Kiểm định',exact:true}).click();
  await row.getByRole('button',{name:'Kết quả',exact:true}).click();
  await staff.getByLabel('Pin (%)',{exact:true}).fill('90');
  await staff.getByLabel('Giá bán (VND)',{exact:true}).fill('1500');
  await staff.getByLabel('Bằng chứng / ghi chú',{exact:true}).fill('Browser inspection');
  await staff.getByRole('button',{name:'Lưu kiểm định',exact:true}).click();
  await row.getByText('AVAILABLE',{exact:true}).waitFor();
  await navigate(staff,'Bán tại quầy');
  await row.getByRole('button',{name:'Chọn bán',exact:true}).click();
  await staff.getByLabel('Tên khách',{exact:true}).fill('Browser Buyer '+run);
  let lost=false, firstKey;
  await staff.route('**/api/orders/checkout',async route=>{
    if(!lost){lost=true;firstKey=route.request().headers()['idempotency-key'];const response=await route.fetch();assert.equal(response.status(),201);await route.abort('failed');}
    else {assert.equal(route.request().headers()['idempotency-key'],firstKey);await route.continue();}
  });
  await staff.getByRole('button',{name:'Chốt bán',exact:true}).click();
  await staff.getByText(/Chưa xác định được kết quả do mất kết nối/).waitFor();
  await staff.getByRole('button',{name:'Chốt bán',exact:true}).click();
  await staff.getByText(/Đã hoàn tất đơn:/).waitFor();
  await navigate(staff,'Đơn bán hàng');
  await staff.getByRole('row').filter({has:staff.getByRole('cell',{name:'Browser Buyer '+run,exact:true})}).getByRole('button',{name:'Chi tiết',exact:true}).click();
  await staff.getByRole('button',{name:'Bảo hành',exact:true}).click();
  await staff.getByRole('button',{name:'Lưu',exact:true}).click();
  await staff.getByText('Đã cấp bảo hành.',{exact:true}).waitFor();
  await navigate(admin,'Lịch sử thao tác');
  await admin.getByRole('cell',{name:'WARRANTY_ISSUED',exact:true}).first().waitFor();
  await admin.screenshot({path:path.resolve(__dirname,'../.run/admin-desktop.png'),fullPage:true});
  await staff.setViewportSize({width:390,height:844});
  await navigate(staff,'Tài khoản của tôi');
  await staff.getByRole('button',{name:'Đăng xuất tất cả',exact:true}).click();
  await staff.locator('#login').waitFor({state:'visible'});
  await admin.reload(); await admin.locator('#login').waitFor({state:'visible'});
  assert.deepEqual(errors,[]);
  console.log('PASS: ADMIN/STAFF lifecycle, lost checkout response replay, warranty, audit, mobile logout and memory-only session.');
 } finally { await browser.close(); }
})().catch(error=>{console.error(error.message);process.exitCode=1;});
