const api = require('../../api/index'), ui = require('../../utils/ui'), session = require('../../utils/session')
Page({
  data: { equipment: null, description: '', imageUrl: '', localImage: '', analyzeImage: false, busy: false, uploading: false, error: '' }, input: ui.input,
  onLoad(options) { this.formToken=session.read()&&session.read().token;this.id = options.id; this.load() },
  onShow() { this.active=true;if(!session.requireLogin())return;if(session.read()&&session.read().token!==this.formToken)wx.switchTab({url:'/pages/equipment/index'});else if(this.submitted)this.finish() },
  onHide(){this.active=false},
  onUnload(){this.active=false;this.unloaded=true},
  async load() {
    if (!session.requireLogin()) return
    try { const equipment=await api.equipmentDetail(this.id);if(!this.unloaded&&session.read()&&session.read().token===this.formToken)this.setData({ equipment,error: '' }) } catch (e) { if(!this.unloaded&&this.active)ui.error.call(this, e) }
  },
  finish(){if(session.manager())wx.setStorageSync('lab_repair_scope',{token:session.read().token,scopeIndex:1});wx.switchTab({url:'/pages/repairs/index'})},
  ai(e) { this.setData({ analyzeImage: e.detail.value }) },
  removeImage() { if (!this.data.busy && !this.data.uploading) this.setData({ imageUrl: '', localImage: '', analyzeImage: false }) },
  async chooseImage() {
    if (this.data.busy || this.data.uploading) return
    if(!session.read()||session.read().token!==this.formToken||this.unloaded)return
    const current=()=>!this.unloaded&&session.read()&&session.read().token===this.formToken
    this.setData({ uploading: true, error: '' })
    try {
      const result = await new Promise((resolve, reject) => wx.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['album', 'camera'], sizeType: ['compressed'], success: resolve, fail: reject }))
      if(!current())return
      const file = result.tempFiles[0]
      if (file.size > 5 * 1024 * 1024) throw new Error('图片不能超过5MB，请压缩后重试')
      const info = await new Promise((resolve, reject) => wx.getImageInfo({ src: file.tempFilePath, success: resolve, fail: reject }))
      if (!['jpeg', 'jpg', 'png'].includes(info.type)) throw new Error('仅支持JPEG或PNG图片，请先转换图片格式')
      if (info.width < 15 || info.height < 15 || info.width > 4096 || info.height > 4096 || info.width * info.height > 16000000) throw new Error('图片宽高须为15–4096像素，总像素不超过1600万')
      if(!current())return
      const uploaded = await api.upload(file.tempFilePath)
      if(!current())return
      this.setData({ imageUrl: uploaded.imageUrl, localImage: file.tempFilePath })
    } catch (e) { if (current() && !/cancel/.test(e.errMsg || '')) ui.error.call(this, e) }
    finally { if(current())this.setData({ uploading: false }) }
  },
  async submit() {
    if (this.submitted || this.data.busy || this.data.uploading || !this.data.equipment || this.unloaded || !session.read() || session.read().token!==this.formToken) return
    const description = this.data.description.trim()
    if (!description) return this.setData({ error: '请填写故障现象' })
    this.setData({ busy: true, error: '' })
    try {
      await api.report({ equipmentId: this.id, description, imageUrl: this.data.imageUrl || null, analyzeImage: !!this.data.imageUrl && this.data.analyzeImage })
      this.submitted=true;
      if(this.unloaded||!this.active||!session.read()||session.read().token!==this.formToken)return
      this.finish(); ui.success('报修已提交')
    } catch (e) { if(!this.unloaded&&this.active)ui.error.call(this, e) } finally { if(!this.unloaded)this.setData({ busy: false }) }
  }
})
