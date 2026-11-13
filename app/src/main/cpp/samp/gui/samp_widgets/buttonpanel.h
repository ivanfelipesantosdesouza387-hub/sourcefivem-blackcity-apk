#pragma once

class ButtonPanel : public Layout
{
public:
    ButtonPanel();

    void performLayout() override;

    // Controles mantidos no HUD: tres botoes visuais (config, radio, chat).
    Widget* m_bSettings;
    Widget* m_bRadio;
    Widget* m_bChat;
};
