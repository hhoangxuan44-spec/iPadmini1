/* ===== 壁纸月历设置 ===== */
var CONFIG = {
  // 壁纸自动切换间隔（分钟）
  intervalMinutes: 5,
  // 手动翻到别的月份后，多久回到本月（秒）
  returnToMonthSeconds: 60,
  // 照片缓慢推拉平移（老设备会自动关闭）
  kenBurns: true,
  // 24 小时制
  use24h: true,
  // 夜间调暗（网页无法直接调 iPad 亮度，用半透明黑色遮罩）
  night: { enabled: true, start: '23:00', end: '07:00', opacity: 0.4 }
};
