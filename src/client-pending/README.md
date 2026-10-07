# Client code parked for the 26.3 port

These files drew the cube loading screen and hooked the section render graph on 1.21.6. Minecraft 26.x rewrote
client rendering (GuiGraphics, RenderType, picture-in-picture render states, SectionOcclusionGraph events), so they
do not compile on 26.3. They are kept here, outside the compiled sources (and outside what generates the mixin
configs), to be rebuilt in the client phase of the port.
