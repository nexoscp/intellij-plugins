@Component({   
  selector: 'my-appww2',  
  template: `<div [style.font-size]="title ? 'medium' : 'small'"></div>`,
  outputs: ['ce', 'de:a']
})       
export class AppComponent {    
  title = 'Tour of Heroes'; 
  heroes = HEROES;
  selectedHero = {firstName: "eee"};
  @Input() ae = 5;
  @Input('eb') be = 5;
   
  ce = 5;  
  de{caret} = 5;
}
