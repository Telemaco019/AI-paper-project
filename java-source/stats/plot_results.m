clear all;
close all;
clc;

files = dir('*.csv');
tot_err = 0;
tot_explored = 0;
tot_mex = 0;
count = 0;

for file = files'
    csv = load(file.name);
    steps = csv(:,1);
    explored = csv(:,2);
    err = csv(:,3);
    messages = csv(:,4);

    tot_err = tot_err + err;
    tot_explored = tot_explored + explored;
    tot_mex = tot_mex + messages;
    count = count + 1;
end


avg_err = tot_err./count;
avg_explored = tot_explored ./ count;
avg_mex = tot_mex ./ count; 

figure(1);
clf
plot(steps,avg_err,'r')
hold on
plot(steps,avg_explored,'b')
legend('% error','% explored')
xlabel('step')
ylabel('percentage')

figure(2); 
clf
plot(steps,avg_mex)

