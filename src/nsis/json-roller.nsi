;NSIS Modern User Interface
;Basic Example Script
;Written by Joost Verburg

;--------------------------------
;Include Modern UI

!include "MUI2.nsh"

!include "EnvVarUpdate.nsh"

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
 
  ${EnvVarUpdate} $0 "PATH" "A" "HKCU" "$INSTDIR"
  ${EnvVarUpdate} $0 "PATH" "A" "HKLM" "$INSTDIR"

  ;Under WinXP this creates two separate buttons: "Modify" and "Remove".
  ;"Modify" will run installer and "Remove" will run uninstaller.
  WriteRegDWord HKLM "${REG_UNINSTALL}" "NoModify" 1
  WriteRegDWord HKLM "${REG_UNINSTALL}" "NoRepair" 0
  WriteRegStr HKLM "${REG_UNINSTALL}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  
  SetOutPath "$INSTDIR"
  
  File ${PROJECT_BUILD_DIR}\json-roller.exe

  ;Store installation folder
  WriteRegStr HKCU "Software\json-roller" "" $INSTDIR
  
  ;Create uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

SectionEnd

Section "Java Runtime Environment" java

  SetOutPath "$INSTDIR\jre"
  File /r "${PROJECT_BASEDIR}\jre\*"
SectionEnd

;--------------------------------
;Uninstaller Section

Section "Uninstall"

  ;ADD YOUR OWN FILES HERE...

  Delete "$INSTDIR\Uninstall.exe"
  Delete "$INSTDIR\json-roller.exe"
  RMDir /r "$INSTDIR"

  ${un.EnvVarUpdate} $0 "PATH" "R" "HKCU" "$INSTDIR"  
  ${un.EnvVarUpdate} $0 "PATH" "R" "HKLM" "$INSTDIR"  
  
  DeleteRegKey /ifempty HKCU "Software\json-roller"
  DeleteRegKey HKLM "${REG_UNINSTALL}"
SectionEnd
