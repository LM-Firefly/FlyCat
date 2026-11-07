import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import ApiPlayground from './components/ApiPlayground.vue'
import Card from './components/Card.vue'
import CardGroup from './components/CardGroup.vue'
import Frame from './components/Frame.vue'
import Mermaid from './components/Mermaid.vue'
import ParamField from './components/ParamField.vue'
import Step from './components/Step.vue'
import Steps from './components/Steps.vue'
import './vars.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('ApiPlayground', ApiPlayground)
    app.component('Card', Card)
    app.component('CardGroup', CardGroup)
    app.component('Frame', Frame)
    app.component('Mermaid', Mermaid)
    app.component('ParamField', ParamField)
    app.component('Step', Step)
    app.component('Steps', Steps)
  }
} satisfies Theme
