;NSIS Modern User Interface
;Basic Example Script
;Written by Joost Verburg

;--------------------------------
;Include Modern UI

!include "MUI2.nsh"
!include "LogicLib.nsh"

!define ENV_HKLM 'HKLM "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"'

;--------------------------------
; StrContains: returns the needle if found in haystack, else ""
; Usage: Push haystack; Push needle; Call [un.]StrContains; Pop $result
!macro Func_StrContains un
Function ${un}StrContains
  Exch $R1 ; needle
  Exch
  Exch $R2 ; haystack
  Push $R3 ; needle length
  Push $R4 ; cursor
  Push $R5 ; window
  StrLen $R3 $R1
  StrCpy $R4 0
  StrCpy $R5 ""
  ${Do}
    StrCpy $R5 $R2 $R3 $R4
    ${If} $R5 == $R1
      ${ExitDo}
    ${EndIf}
    ${If} $R5 == ""
      ${ExitDo}
    ${EndIf}
    IntOp $R4 $R4 + 1
  ${Loop}
  StrCpy $R1 $R5
  Pop $R5
  Pop $R4
  Pop $R3
  Pop $R2
  Exch $R1
FunctionEnd
!macroend

;--------------------------------
; StrReplace: replaces all occurrences of needle in haystack with replacement
; Usage: Push haystack; Push needle; Push replacement; Call [un.]StrReplace; Pop $result
!macro Func_StrReplace un
Function ${un}StrReplace
  Exch $R0 ; replacement
  Exch
  Exch $R1 ; needle
  Exch 2
  Exch $R2 ; haystack
  Push $R3 ; needle length
  Push $R4 ; cursor
  Push $R5 ; accumulator
  Push $R6 ; window
  Push $R7 ; char
  StrLen $R3 $R1
  StrCpy $R4 0
  StrCpy $R5 ""
  ${Do}
    StrCpy $R6 $R2 $R3 $R4
    ${If} $R6 == ""
      ; copy any remaining tail
      StrCpy $R7 $R2 "" $R4
      StrCpy $R5 "$R5$R7"
      ${ExitDo}
    ${EndIf}
    ${If} $R6 == $R1
      StrCpy $R5 "$R5$R0"
      IntOp $R4 $R4 + $R3
    ${Else}
      StrCpy $R7 $R2 1 $R4
      StrCpy $R5 "$R5$R7"
      IntOp $R4 $R4 + 1
    ${EndIf}
  ${Loop}
  StrCpy $R0 $R5
  Pop $R7
  Pop $R6
  Pop $R5
  Pop $R4
  Pop $R3
  Pop $R2
  Pop $R1
  Exch $R0
FunctionEnd
!macroend

!insertmacro Func_StrContains ""
!insertmacro Func_StrContains "un."
!insertmacro Func_StrReplace ""
!insertmacro Func_StrReplace "un."

;--------------------------------
; AddToSystemPath: append $INSTDIR to the system PATH (HKLM) if not present
!macro AddToSystemPath
  Push $0
  Push $1
  ReadRegStr $0 ${ENV_HKLM} "Path"
  Push ";$0;"
  Push ";$INSTDIR;"
  Call StrContains
  Pop $1
  ${If} $1 == ""
    ${If} $0 == ""
      WriteRegExpandStr ${ENV_HKLM} "Path" "$INSTDIR"
    ${Else}
      WriteRegExpandStr ${ENV_HKLM} "Path" "$0;$INSTDIR"
    ${EndIf}
    SendMessage ${HWND_BROADCAST} ${WM_WININICHANGE} 0 "STR:Environment" /TIMEOUT=5000
  ${EndIf}
  Pop $1
  Pop $0
!macroend

;--------------------------------
; RemoveFromSystemPath: remove $INSTDIR from the system PATH (HKLM) if present
!macro un.RemoveFromSystemPath
  Push $0
  Push $1
  Push $2
  ReadRegStr $0 ${ENV_HKLM} "Path"
  Push ";$0;"
  Push ";$INSTDIR;"
  Call un.StrContains
  Pop $1
  ${If} $1 != ""
    ; Replace ";$INSTDIR;" with ";" in the wrapped string, then trim wrapping ";"
    Push ";$0;"
    Push ";$INSTDIR;"
    Push ";"
    Call un.StrReplace
    Pop $1
    ; Strip leading ";"
    StrCpy $1 $1 "" 1
    ; Strip trailing ";"
    StrLen $2 $1
    IntOp $2 $2 - 1
    StrCpy $1 $1 $2
    WriteRegExpandStr ${ENV_HKLM} "Path" "$1"
    SendMessage ${HWND_BROADCAST} ${WM_WININICHANGE} 0 "STR:Environment" /TIMEOUT=5000
  ${EndIf}
  Pop $2
  Pop $1
  Pop $0
!macroend

;--------------------------------
;General

  ;Name and file
  Name "json-roller"

  ;Default installation folder
  InstallDir "$PROGRAMFILES\json-roller"
  
  ;Get installation folder from registry if available
  InstallDirRegKey HKCU "Software\json-roller" ""

  ;Request application privileges for Windows Vista
  RequestExecutionLevel admin
  
  !define REG_UNINSTALL "Software\Microsoft\Windows\CurrentVersion\Uninstall\json-roller"

;--------------------------------
;Interface Settings

  !define MUI_ABORTWARNING

;--------------------------------
;Pages

  !insertmacro MUI_PAGE_COMPONENTS
  !insertmacro MUI_PAGE_DIRECTORY
  !insertmacro MUI_PAGE_INSTFILES
  
  !insertmacro MUI_UNPAGE_CONFIRM
  !insertmacro MUI_UNPAGE_INSTFILES
  
  
;--------------------------------
;Languages
 
  !insertmacro MUI_LANGUAGE "English"

;--------------------------------
;Installer Sections

Section "json-roller" Main
SectionIn RO
  WriteRegStr HKLM "${REG_UNINSTALL}" "DisplayName" "JSON Roller"
  WriteRegStr HKLM "${REG_UNINSTALL}" "DisplayIcon" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "${REG_UNINSTALL}" "DisplayVersion" "1.0"
  WriteRegStr HKLM "${REG_UNINSTALL}" "Publisher" "openstatic.org"
  WriteRegStr HKLM "${REG_UNINSTALL}" "InstallSource" "$EXEDIR\"
 
  !insertmacro AddToSystemPath

  ;Under WinXP this creates two separate buttons: "Modify" and "Remove".
  ;"Modify" will run installer and "Remove" will run uninstaller.
  WriteRegDWord HKLM "${REG_UNINSTALL}" "NoModify" 1
  WriteRegDWord HKLM "${REG_UNINSTALL}" "NoRepair" 0
  WriteRegStr HKLM "${REG_UNINSTALL}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  
  SetOutPath "$INSTDIR"

  ; Native-image produced executable
  File ${PROJECT_BUILD_DIR}\json-roller.exe

  ; Include every DLL native-image emitted alongside the binary
  ; (/nonfatal so the build still succeeds if there are none)
  File /nonfatal ${PROJECT_BUILD_DIR}\*.dll

  ;Store installation folder
  WriteRegStr HKCU "Software\json-roller" "" $INSTDIR
  
  ;Create uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

SectionEnd

;--------------------------------
;Uninstaller Section

Section "Uninstall"

  ;ADD YOUR OWN FILES HERE...

  Delete "$INSTDIR\Uninstall.exe"
  Delete "$INSTDIR\json-roller.exe"
  Delete "$INSTDIR\*.dll"
  RMDir /r "$INSTDIR"

  !insertmacro un.RemoveFromSystemPath
  
  DeleteRegKey /ifempty HKCU "Software\json-roller"
  DeleteRegKey HKLM "${REG_UNINSTALL}"
SectionEnd
