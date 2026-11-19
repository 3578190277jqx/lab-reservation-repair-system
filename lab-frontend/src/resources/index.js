import lab from './lab'
import equipment from './equipment'
import reservation from './reservation'
import repair from './repair'
import user from './user'

export const resourceDefinitions = { lab, equipment, reservation, repair, user }
export const roleNames = { admin: '系统管理员', lab_admin: '实验室管理员', student: '学生' }
