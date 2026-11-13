import { useEffect, useState } from "react";

const read = (key,fallback) => localStorage.getItem(key) || fallback;
const isEvening = () => { const hour=new Date().getHours(); return hour>=19||hour<6; };

export default function useAppearance() {
  const [theme,setTheme] = useState(()=>read("danbank-theme","system"));
  const [eyeMode,setEyeMode] = useState(()=>read("danbank-eye-mode","auto"));
  const [evening,setEvening] = useState(isEvening);

  useEffect(()=>{const timer=setInterval(()=>setEvening(isEvening()),60000);return()=>clearInterval(timer)},[]);
  useEffect(()=>{
    const media=window.matchMedia("(prefers-color-scheme: dark)");
    const apply=()=>{document.documentElement.dataset.theme=theme==="system"?(media.matches?"dark":"light"):theme;localStorage.setItem("danbank-theme",theme)};
    apply();media.addEventListener("change",apply);return()=>media.removeEventListener("change",apply);
  },[theme]);
  useEffect(()=>{const enabled=eyeMode==="on"||(eyeMode==="auto"&&evening);document.documentElement.dataset.eyeComfort=String(enabled);localStorage.setItem("danbank-eye-mode",eyeMode)},[eyeMode,evening]);

  return { theme,setTheme,eyeMode,setEyeMode,eyeComfortActive:eyeMode==="on"||(eyeMode==="auto"&&evening),evening };
}
