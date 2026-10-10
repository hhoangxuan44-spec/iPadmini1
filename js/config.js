/* ===== 台历设置 ===== */
var CONFIG = {
  // 翻到别的日期后，多久没碰自动回到今天（分钟）
  returnToTodayMinutes: 3,
  // 右上角显示时间
  showClock: true,
  // 倾斜设备时：照片视差 + 玻璃高光跟随（老设备觉得卡可以关掉）
  tiltEffects: true,
  // 夜间调暗（网页无法直接调节 iPad 亮度，用半透明黑色遮罩）
  night: { enabled: true, start: '23:00', end: '07:00', opacity: 0.55 }
};
