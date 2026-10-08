# Client code parked for the 26.3 port

These files drew the cube loading screen on 1.21.6: a map of cube generation statuses drawn while a world loads.
Minecraft 26.x rewrote the loading screen (LevelLoadTracker, picture-in-picture render states) and GUI drawing, so
they do not compile on 26.3; until they are rebuilt, cubic worlds show vanilla's loading screen. They are kept here,
outside the compiled sources (and outside what generates the mixin configs).

The render-graph hooks that used to be parked here were rewritten for 26.3's RotatingSectionStorage, render-state
extraction and SectionUpdateTracker (see client/renderer/CubicRenderSections and the mixins it names).
