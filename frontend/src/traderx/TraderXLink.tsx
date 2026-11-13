import { useEffect, useState } from 'react'
export function TraderXLink() {
  const [url,setUrl]=useState('')
  useEffect(() => { let active=true; fetch('/api/demo/traderx/environment').then(r => r.ok ? r.json() : Promise.reject()).then(d => {
    const parsed=new URL(d.publicUrl); if(active && ['http:','https:'].includes(parsed.protocol)) setUrl(parsed.href)
  }).catch(() => {}); return () => {active=false} },[])
  return url ? <a className="text-link" href={url} target="_blank" rel="noreferrer">Open TraderX ↗</a> : null
}
