const api = require('../../api/index'), ui = require('../../utils/ui'), session = require('../../utils/session')
Page({
  data: { equipment: null, startDate: '', endDate: '', startClock: '09:00', endClock: '10:00', purpose: '', busy: false, error: '', schedule: null, scheduleDate: '', scheduleLoading: false, scheduleError: '' },
  input(e) { ui.input.call(this, e); if(e.currentTarget.dataset.field === 'startDate') { this.setData({scheduleDate: e.detail.value}); this.loadSchedule() } },
  onLoad(options) { this.formToken=session.read()&&session.read().token;this.id = options.id; const date = ui.dateNow(); this.setData({ startDate: date, endDate: date, scheduleDate: date }); this.load() },
  onShow() { this.active=true;if(!session.requireStudent())return;if(session.read()&&session.read().token!==this.formToken)wx.switchTab({url:'/pages/equipment/index'});else if(this.submitted)wx.switchTab({url:'/pages/reservations/index'});else if(this.data.equipment)this.loadSchedule() },
  onHide(){this.active=false;this.scheduleSequence=(this.scheduleSequence||0)+1;this.setData({scheduleLoading:false})},
  onUnload(){this.active=false;this.unloaded=true;this.scheduleSequence=(this.scheduleSequence||0)+1},
  chooseScheduleDate(e) { this.setData({scheduleDate:e.detail.value});this.loadSchedule() },
  async loadSchedule() {
    const sequence = this.scheduleSequence = (this.scheduleSequence || 0) + 1
    const current = () => !this.unloaded && this.active && sequence === this.scheduleSequence
      && session.read() && session.read().token === this.formToken
    if (!current() || !this.id || !this.data.scheduleDate) return
    this.setData({scheduleLoading:true,scheduleError:'',schedule:null})
    try {
      const schedule = await api.schedule({equipmentId:this.id,date:this.data.scheduleDate})
      if(!current())return
      if(!schedule || !Array.isArray(schedule.slots))throw new Error('占用时段返回格式错误')
      this.setData({schedule})
    } catch(e) { if(current() && e.code !== 'ERR_AUTH_CHANGED')this.setData({scheduleError:e.message || '占用时段加载失败'}) }
    finally { if(current())this.setData({scheduleLoading:false}) }
  },
  async load() {
    if (!session.requireStudent()) return
    try { const equipment=await api.equipmentDetail(this.id);if(!this.unloaded&&session.read()&&session.read().token===this.formToken){ this.setData({ equipment,error: '' });this.loadSchedule() } } catch (e) { if(!this.unloaded&&this.active)ui.error.call(this, e) }
  },
  async submit() {
    if (this.submitted || this.data.busy || !this.data.equipment || this.unloaded || !session.read() || session.read().token!==this.formToken) return
    const { startDate, endDate, startClock, endClock } = this.data
    const startTime = startDate + ' ' + startClock + ':00', endTime = endDate + ' ' + endClock + ':00'
    if (ui.time(startTime) <= Date.now() || ui.time(endTime) <= ui.time(startTime)) return this.setData({ error: '开始时间须晚于现在，结束时间须晚于开始时间（北京时间）' })
    const purpose = this.data.purpose.trim()
    if (!purpose) return this.setData({ error: '请填写预约用途' })
    this.setData({ busy: true, error: '' })
    try { await api.apply({ equipmentId: this.id, startTime, endTime, purpose }); this.submitted=true;if(this.active&&!this.unloaded&&session.read()&&session.read().token===this.formToken){wx.switchTab({ url: '/pages/reservations/index' }); ui.success('申请已提交')} }
    catch (e) { if(!this.unloaded&&this.active){ui.error.call(this, e);this.loadSchedule()} } finally { if(!this.unloaded)this.setData({ busy: false }) }
  }
})
